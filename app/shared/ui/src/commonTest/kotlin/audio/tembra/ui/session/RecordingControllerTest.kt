package audio.tembra.ui.session

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.core.recording.MonitorState
import audio.tembra.core.recording.RecordingState
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.data.projects.AdoptedTake
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.timeline.FakeTimelinePreferences
import audio.tembra.engine.ArmResult
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.TakeInfo
import audio.tembra.ui.platform.FakeAudioRouteMonitor
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.platform.PlaybackNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The controller against the fakes (contracts/recording-session.md "Tests"; feature 010 T021): the permission flow,
 * the arm, the staging before the engine starts, the pre-roll, the adoption into one edit, the engine-initiated
 * reasons, leaving, the routes, the background host and the remembered monitor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drums = demo.tracks[0].id
    private val bass = demo.tracks[1].id
    private val engine = FakeAudioEngine()
    private val permission = FakeMicrophonePermission(MicrophonePermissionStatus.Granted)
    private val routes = FakeAudioRouteMonitor()
    private val host = FakeBackgroundPlaybackHost()
    private val preferences = FakeTimelinePreferences()
    private val repository = FakeProjectRepository(initial = listOf(demo))
    private val sessionHost = FakeRecordingHost(demo)
    private var tokenCounter = 0
    private val controller = RecordingController(
        audio = engine,
        permission = permission,
        routes = routes,
        host = host,
        preferences = preferences,
        repository = repository,
        scope = scope,
        clock = { 1_000L },
        tokens = { "take-${++tokenCounter}" },
        mirrorIntervalMs = 100,
    )

    @AfterTest
    fun tearDown() = scope.cancel()

    /**
     * `runTest` advances the virtual clock until idle once the body returns; the controller's 10 Hz mirror never goes
     * idle while a track stays armed, so the session scope is cancelled first.
     */
    private fun recordingTest(block: suspend TestScope.() -> Unit): TestResult = runTest(dispatcher) {
        try {
            block()
        } finally {
            scope.cancel()
        }
    }

    private suspend fun TestScope.startArmed(trackId: TrackId = drums) {
        engine.openTransport()
        controller.start(sessionHost)
        runCurrent()
        controller.arm(trackId)
        runCurrent()
        assertEquals(RecordingState.Armed(trackId), controller.state.value.phase)
    }

    private fun phase(): RecordingState = controller.state.value.phase

    private fun calls(): List<String> = engine.calls.map { it.substringBefore('(') }

    // --- Arming and the permission -----------------------------------------------------------------------------------

    @Test
    fun armOpensTheInputOnceGrantedAndTheTelemetryIsReadable() = recordingTest {
        startArmed()
        assertEquals(1, calls().count { it == "arm" })
        assertEquals(drums, controller.state.value.armedTrack)
        assertEquals(1, controller.state.value.inputChannels)
        val snapshot = CaptureSnapshot()
        controller.inputTelemetry.read(1L, snapshot)
        assertTrue(snapshot.armed)
        assertEquals(48000, snapshot.sampleRate)
        // A second read in the same frame does not touch the engine again.
        val reads = engine.readCaptureCount
        controller.inputTelemetry.read(1L, snapshot)
        assertEquals(reads, engine.readCaptureCount)
        controller.arm(drums)  // the lit toggle disarms
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals("disarm", engine.calls.last())
    }

    @Test
    fun armRequestsThePermissionOnceAndArmsOnAGrant() = recordingTest {
        permission.set(MicrophonePermissionStatus.NotDetermined)
        permission.requestResults += MicrophonePermissionStatus.Granted
        engine.openTransport()
        controller.start(sessionHost)
        runCurrent()
        controller.arm(drums)
        runCurrent()
        assertEquals(1, permission.requestCount)
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(MicrophonePermissionStatus.Granted, controller.state.value.permission)
    }

    @Test
    fun aDeniedPermissionLeavesTheTrackUnarmedWithTheMessage() = recordingTest {
        permission.set(MicrophonePermissionStatus.DeniedCanAsk)
        permission.requestResults += MicrophonePermissionStatus.DeniedOpenSettings
        engine.openTransport()
        controller.start(sessionHost)
        runCurrent()
        controller.arm(drums)
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.MicrophoneDenied(openSettings = true)), sessionHost.messages)
        assertFalse(calls().contains("arm"))
        // Denied for good: the arm shows the message at once and the action opens the settings.
        controller.arm(drums)
        runCurrent()
        assertEquals(2, sessionHost.messages.size)
        controller.requestMicrophone()
        assertEquals(1, permission.openSettingsCount)
    }

    @Test
    fun aDeviceWithoutAMicrophoneSaysSoAndAsksForNothing() = recordingTest {
        // Feature 011 (FR-002) reaches desktops without a microphone: arming there names the cause, prompts for nothing
        // and opens no input, as Auto Listen does.
        permission.set(MicrophonePermissionStatus.Unavailable)
        engine.openTransport()
        controller.start(sessionHost)
        runCurrent()
        controller.arm(drums)
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.NoMicrophone), sessionHost.messages)
        assertFalse(calls().contains("arm"))
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun anInputThatCannotOpenReportsItAndStaysDisarmed() = recordingTest {
        engine.openTransport()
        engine.armResult = ArmResult.InputUnavailable
        controller.start(sessionHost)
        runCurrent()
        controller.arm(drums)
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(TakeStopReason.InputLost)), sessionHost.messages)
    }

    @Test
    fun armingAnotherTrackMovesTheArm() = recordingTest {
        startArmed(drums)
        controller.arm(bass)
        runCurrent()
        assertEquals(RecordingState.Armed(bass), phase())
        assertEquals(listOf("openTransport", "arm", "disarm", "arm"), calls())
    }

    // --- Takes -------------------------------------------------------------------------------------------------------

    @Test
    fun recordFromStoppedStagesTheManifestBeforeTheEngineStartsWithTheCountIn() = recordingTest {
        startArmed()
        sessionHost.position = Beats(8.0)
        engine.takeWriter = { assertEquals(1, repository.stagedTakes.size, "the manifest is written before recordStart") }
        controller.record(countInBeats = 4)
        runCurrent()
        val manifest = repository.stagedTakes.single()
        assertEquals("take-1", manifest.token)
        assertEquals(drums, manifest.trackId)
        assertEquals(Beats(8.0), manifest.startBeats)
        assertEquals("Take 1", manifest.takeName)
        assertEquals(1_000L, manifest.startedAt)
        val config = engine.lastRecordConfig!!
        assertEquals(4, config.countInBeats)
        assertEquals(0, config.trackIndex)
        assertEquals(repository.stagedTakes.single().let { "/fake/${demo.id.value}/audio/.take-${it.token}.wav" }, config.wavPath)
        assertEquals(RecordingState.PreRoll(drums, "take-1", 4), phase())
        assertEquals(PlaybackNotification.recording(demo.name), host.notification)
        assertEquals(0, sessionHost.playbackStarted)
        val live = controller.state.value.liveTake!!
        assertEquals("Take 1", live.clipName)
        assertEquals(Beats(8.0), live.startBeats)
    }

    @Test
    fun thePreRollTicksReachTheStateAndTheTakeStartsOnTheDownbeat() = recordingTest {
        startArmed()
        controller.record(countInBeats = 4)
        runCurrent()
        engine.nextTransport.countInBeatsLeft = 3
        advanceTimeBy(101)
        runCurrent()
        assertEquals(RecordingState.PreRoll(drums, "take-1", 3), phase())
        engine.endPreRoll()
        engine.nextCapture.takeStartFrames = 92_903  // beat 4 at the demo's 124 BPM
        advanceTimeBy(100)
        runCurrent()
        val recording = assertIs<RecordingState.Recording>(phase())
        assertEquals(4.0, recording.startBeats.value, 1e-4)
        assertEquals(1, sessionHost.playbackStarted)
        assertTrue(controller.state.value.takeRunning)
    }

    @Test
    fun stopAdoptsTheTakeIntoOneEditAndTheTrackStaysArmed() = recordingTest {
        startArmed()
        sessionHost.isPlaying = true
        controller.record(countInBeats = 4)  // playing: no count-in
        runCurrent()
        assertIs<RecordingState.Recording>(phase())
        assertEquals(0, engine.lastRecordConfig!!.countInBeats)
        assertEquals(1, sessionHost.playbackStarted)
        controller.stop()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(1, repository.adoptedTakes.size)
        val (edit, audioPath, overviewPath) = sessionHost.appliedTakes.single()
        assertEquals(drums, edit.trackId)
        assertEquals("Take 1", edit.clip.name)
        assertTrue(audioPath.endsWith(".wav"))
        assertTrue(overviewPath.endsWith(".peaks"))
        assertEquals(listOf("openTransport", "arm", "recordStart", "recordStop"), calls())
        // The host went back to the playing notification; nothing released while playback runs.
        assertEquals(PlaybackNotification("Playing", demo.name), host.notification)
        assertNull(controller.state.value.liveTake)
    }

    @Test
    fun aTakeUnderAHundredthOfASecondLeavesNoEdit() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        engine.recordStopResult = TakeInfo(startFrames = 0, startBeats = 0.0, frames = 0, channels = 1, sampleRate = 48000, alignmentFrames = 0, stopReason = TakeStopReason.User)
        controller.stop()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertTrue(sessionHost.appliedTakes.isEmpty())
        assertEquals(1, repository.adoptedTakes.size)
    }

    @Test
    fun recordIsRefusedBelow200MbOfFreeSpace() = recordingTest {
        startArmed()
        engine.freeBytesResult = 100L * 1024 * 1024
        controller.record(countInBeats = 0)
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.RecordRefused), sessionHost.messages)
        assertFalse(calls().contains("recordStart"))
    }

    @Test
    fun theEngineEndingTheTakeItselfReachesTheMessageAndTheAdoption() = recordingTest {
        for (reason in listOf(TakeStopReason.MaxLength, TakeStopReason.DiskFull, TakeStopReason.InputLost, TakeStopReason.WriteFailed)) {
            sessionHost.messages.clear()
            sessionHost.appliedTakes.clear()
            if (phase() == RecordingState.Disarmed) startArmed()
            controller.record(countInBeats = 0)
            runCurrent()
            engine.endTake(reason)
            advanceTimeBy(101)
            runCurrent()
            assertEquals(RecordingState.Armed(drums), phase(), "$reason")
            assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(reason)), sessionHost.messages, "$reason")
            assertEquals(1, sessionHost.appliedTakes.size, "$reason: the take so far is kept")
        }
    }

    @Test
    fun cancelDuringTheCountInDeletesTheManifestAndStopsPlayback() = recordingTest {
        startArmed()
        controller.record(countInBeats = 4)
        runCurrent()
        controller.cancel()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals("recordCancel", engine.calls.last())
        assertEquals(1, sessionHost.playbackStoppedByCancel)
        assertTrue(repository.staged[demo.id].isNullOrEmpty(), "the manifest is discarded")
        assertTrue(sessionHost.appliedTakes.isEmpty())
    }

    // --- Leaving and the platform ----------------------------------------------------------------------------------

    @Test
    fun hostHiddenDuringATakeEndsItKeptAndDisarms() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        controller.hostHidden()
        assertIs<RecordingState.Finalising>(phase())
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals(1, sessionHost.appliedTakes.size)
        assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(TakeStopReason.Leaving)), sessionHost.messages)
        assertEquals("disarm", engine.calls.last())
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun awaitTakeEndedReturnsOnceTheTakeIsAdopted() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        var returned = false
        val waiter = scope.launch { controller.awaitTakeEnded(); returned = true }
        runCurrent()
        assertFalse(returned)
        controller.projectClosing()
        runCurrent()
        assertTrue(returned)
        assertEquals(RecordingState.Disarmed, phase())
        waiter.cancel()
    }

    @Test
    fun anInterruptionEndsTheTakeAndADeletedArmedTrackDisarms() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        controller.interrupted()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(TakeStopReason.Interrupted)), sessionHost.messages)
        controller.trackDeleted(bass)
        assertEquals(RecordingState.Armed(drums), phase())
        controller.trackDeleted(drums)
        assertEquals(RecordingState.Disarmed, phase())
    }

    @Test
    fun aRevokedPermissionWhileArmedDisarmsWithTheMessage() = recordingTest {
        startArmed()
        permission.set(MicrophonePermissionStatus.DeniedOpenSettings)
        runCurrent()
        assertEquals(RecordingState.Disarmed, phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.MicrophoneDenied(openSettings = true)), sessionHost.messages)
    }

    @Test
    fun aNewInputDeviceEndsTheTakeWithInputLost() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        routes.plugHeadset()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(TakeStopReason.InputLost)), sessionHost.messages)
    }

    // --- Monitoring --------------------------------------------------------------------------------------------------

    @Test
    fun theRememberedMonitorFollowsTheArmAndTheSpeakerSuspendsIt() = recordingTest {
        preferences.setMonitorInput(true)
        routes.plugHeadphones()
        startArmed()
        assertEquals(MonitorState.On, controller.state.value.monitor)
        assertEquals("setMonitor(true,0)", engine.calls.last())
        routes.unplugHeadphones()
        runCurrent()
        assertEquals(MonitorState.SuspendedSpeaker, controller.state.value.monitor)
        assertEquals("setMonitor(false,0)", engine.calls.last())
        routes.plugHeadphones()
        runCurrent()
        assertEquals(MonitorState.On, controller.state.value.monitor)
        assertEquals("setMonitor(true,0)", engine.calls.last())
    }

    @Test
    fun theToggleIsRememberedOnTheDeviceAndRefusedOnTheSpeaker() = recordingTest {
        startArmed()
        controller.toggleMonitor()
        runCurrent()
        assertEquals(MonitorState.Off, controller.state.value.monitor)
        assertEquals(listOf<SessionMessage>(SessionMessage.MonitorNeedsHeadphones), sessionHost.messages)
        assertFalse(preferences.storedMonitorInput)
        routes.plugHeadphones()
        runCurrent()
        controller.toggleMonitor()
        runCurrent()
        assertEquals(MonitorState.On, controller.state.value.monitor)
        assertTrue(preferences.storedMonitorInput)
        assertEquals("setMonitor(true,0)", engine.calls.last())
        controller.disarm()
        runCurrent()
        assertEquals(MonitorState.Off, controller.state.value.monitor)
    }

    @Test
    fun theLivePeaksFillTheGrowingTakesOverview() = recordingTest {
        startArmed()
        controller.record(countInBeats = 0)
        runCurrent()
        engine.scriptedPeaks = { from, out ->
            out.count = 4
            for (i in 0 until 4) { out.min[i * 2] = -40; out.max[i * 2] = 40 }
            out.firstIndex = from
            out.nextIndex = from + 4
            from + 4
        }
        engine.nextCapture.framesCaptured = 1024
        advanceTimeBy(101)
        runCurrent()
        val live = controller.state.value.liveTake!!
        assertEquals(1024L, live.framesCaptured)
        val overview = live.overview!!
        assertEquals(1024L, overview.frames)
        assertTrue(overview.levels.isNotEmpty())
        controller.stop()
        runCurrent()
        assertNull(controller.state.value.liveTake)
    }

    @Test
    fun aFailedAdoptionIsReportedAndTheTrackStaysArmed() = recordingTest {
        startArmed()
        repository.adoptResult = { _, _ -> AdoptedTake.Failed("inspect disagreed") }
        controller.record(countInBeats = 0)
        runCurrent()
        controller.stop()
        runCurrent()
        assertEquals(RecordingState.Armed(drums), phase())
        assertEquals(listOf<SessionMessage>(SessionMessage.TakeEnded(TakeStopReason.WriteFailed)), sessionHost.messages)
        assertTrue(sessionHost.appliedTakes.isEmpty())
    }
}
