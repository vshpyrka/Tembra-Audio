package audio.tembra.ui.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.PitchDirection
import audio.tembra.core.tuner.TunerMode
import audio.tembra.data.tuner.FakeTunerPreferences
import audio.tembra.data.tuner.TunerPreferencesSnapshot
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.TestSignals
import audio.tembra.ui.platform.FakeMicrophonePermission
import androidx.lifecycle.ViewModelStore
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ViewModel against the three fakes (contracts/tuner-screen.md): the microphone lease, the permission flow,
 * selectors and persistence, auditions, the loop toggle, interruptions and the 10 Hz poll.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var audio: FakeAudioEngine
    private lateinit var permission: FakeMicrophonePermission
    private lateinit var preferences: FakeTunerPreferences
    private var recordings: Map<String, ByteArray> = emptyMap()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        audio = FakeAudioEngine()
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.Granted)
        preferences = FakeTunerPreferences()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private var created: TunerViewModel? = null

    /** Builds the ViewModel over the fakes as configured so far; nothing runs until the screen shows it. */
    private fun TestScope.viewModel(decodeDispatcher: CoroutineDispatcher = dispatcher): TunerViewModel {
        val repository = ReferenceSoundRepository { path -> recordings[path] }
        val viewModel = TunerViewModel(audio, permission, preferences, repository, decodeDispatcher)
        created = viewModel
        runCurrent()
        return viewModel
    }

    /** The screen became visible (its wiring calls this from composition); the ViewModel loads and starts here. */
    private fun TestScope.show(viewModel: TunerViewModel) {
        viewModel.screenVisible(true)
        runCurrent()
    }

    /** Holds every dispatched block until [release], so a recording's decode can be left in flight. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held += block
        }

        fun release() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    /**
     * Runs [block] on the test scheduler and clears the ViewModel it created afterwards: the 10 Hz poll is a periodic
     * coroutine, so virtual time is stepped with `runCurrent` and `advanceTimeBy`, never `advanceUntilIdle`.
     */
    private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            created?.clear()
            created = null
            runCurrent()
        }
    }

    /** Clears the ViewModel the way its store would (`onCleared` is protected). */
    private fun TunerViewModel.clear() = ViewModelStore().apply { put("tuner", this@clear) }.clear()

    private fun TestScope.enterAutoListen(viewModel: TunerViewModel) {
        viewModel.screenVisible(true)
        viewModel.foreground(true)
        runCurrent()
    }

    private fun TestScope.enterByEar(viewModel: TunerViewModel) {
        viewModel.selectMode(TunerMode.ByEar)
        enterAutoListen(viewModel)
    }

    // --- Listening (US1) --------------------------------------------------------------------------------------------

    @Test
    fun nothingRunsUntilTheScreenFirstShows() = viewModelTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.loaded, "preferences were read before the screen showed")
        show(viewModel)
        assertTrue(viewModel.uiState.value.loaded)
    }

    @Test
    fun loadsThePreferenceDefaults() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val state = viewModel.uiState.value
        assertTrue(state.loaded)
        assertEquals(Instrument.Guitar, state.instrument)
        assertEquals(GuitarStringCount.Six, state.guitarStrings)
        assertEquals("guitar6-standard", state.preset.id)
        assertEquals(TunerMode.AutoListen, state.mode)
        assertTrue(state.loopTone)
        assertEquals(MicrophonePermissionStatus.Granted, state.microphone)
        assertTrue(audio.calls.isEmpty())
    }

    @Test
    fun visibleAndForegroundStartsListening() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(listOf("startListening"), audio.calls)
        assertEquals(AudioState.Listening, viewModel.uiState.value.audio)
        assertEquals(AudioStatePill.MicActive, viewModel.uiState.value.pill)
    }

    @Test
    fun leavingTheScreenStopsListening() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.screenVisible(false)
        runCurrent()
        assertEquals(listOf("startListening", "stopListening"), audio.calls)
        assertEquals(AudioState.Idle, viewModel.uiState.value.audio)
        assertNull(viewModel.uiState.value.pill)
    }

    @Test
    fun backgroundingStopsAndReturningRestarts() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.foreground(false)
        runCurrent()
        assertEquals(listOf("startListening", "stopListening"), audio.calls)
        viewModel.foreground(true)
        runCurrent()
        assertEquals(listOf("startListening", "stopListening", "startListening"), audio.calls)
    }

    @Test
    fun resumeOnlyRefreshesThePermission() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        val before = audio.calls.toList()
        viewModel.onResumed()
        runCurrent()
        assertEquals(1, permission.refreshCount)
        assertEquals(before, audio.calls)
    }

    @Test
    fun switchingToByEarStopsListening() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.selectMode(TunerMode.ByEar)
        runCurrent()
        assertEquals(listOf("startListening", "stopListening"), audio.calls)
        assertEquals(TunerMode.ByEar, preferences.snapshot.first().mode)
    }

    @Test
    fun anUndeterminedPermissionIsRequestedOnceAndListeningFollowsAGrant() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.NotDetermined)
        permission.requestResults += MicrophonePermissionStatus.Granted
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(1, permission.requestCount)
        assertEquals(listOf("startListening"), audio.calls)
    }

    @Test
    fun aDeniedPermissionNeverStartsListening() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.DeniedOpenSettings)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertTrue(audio.calls.isEmpty())
        assertTrue(viewModel.uiState.value.showDeniedState)
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun aFailedStartLeavesIdleAndRetriesOnTheNextChange() = viewModelTest {
        audio.failNextStart = AudioResult.Error(-2)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(AudioState.Idle, viewModel.uiState.value.audio)
        assertEquals(listOf("startListening"), audio.calls)
        viewModel.foreground(false)
        viewModel.foreground(true)
        runCurrent()
        assertEquals(listOf("startListening", "startListening"), audio.calls)
        assertEquals(AudioState.Listening, viewModel.uiState.value.audio)
    }

    @Test
    fun thePollSamplesTheReadingAtTenHertz() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        audio.nextSnapshot.apply {
            hasPitch = true
            frequencyHz = 83.37f
        }
        advanceTimeBy(150)
        val reading = viewModel.uiState.value.reading
        assertNotNull(reading)
        assertEquals(Note.parse("E2"), reading.note)
        assertTrue(abs(reading.cents - 20.0) < 0.2, "cents ${reading.cents}")
        assertEquals(PitchDirection.Sharp, reading.direction)
        audio.nextSnapshot.hasPitch = false
        advanceTimeBy(150)
        assertNull(viewModel.uiState.value.reading)
        val reads = audio.readCount
        advanceTimeBy(1000)
        assertTrue(audio.readCount >= reads + 9, "poll read ${audio.readCount - reads} times in a second")
    }

    @Test
    fun theWatchdogStopIsNoticedAndListeningRestarts() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        audio.simulateWatchdog()
        advanceTimeBy(150)
        // The engine already closed the stream; the poll notices and starts again without a redundant stop.
        assertEquals(listOf("startListening", "startListening"), audio.calls)
        assertEquals(AudioState.Listening, viewModel.uiState.value.audio)
    }

    @Test
    fun clearingStopsEverything() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.clear()
        assertEquals(listOf("startListening", "stopListening"), audio.calls)
    }

    @Test
    fun aTestSignalListensThroughTheSyntheticDeviceWithoutAPrompt() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.NotDetermined)
        val viewModel = viewModel()
        val signal = TestSignals.byId("e2-flat-20")!!.signal
        viewModel.useTestSignal(signal)
        enterAutoListen(viewModel)
        assertEquals(listOf("startTestSignal"), audio.calls)
        assertEquals(0, permission.requestCount)
        assertEquals(AudioStatePill.TestSignal, viewModel.uiState.value.pill)
        assertEquals(signal, audio.lastTestSignal)
        permission.requestResults += MicrophonePermissionStatus.Granted
        viewModel.useTestSignal(null)
        runCurrent()
        assertEquals(listOf("startTestSignal", "stopListening", "startListening"), audio.calls)
        assertEquals(1, permission.requestCount)
    }

    // --- Selectors (US2) ---------------------------------------------------------------------------------------------

    @Test
    fun selectingTheInstrumentPicksItsDefaultPresetAndPersists() = viewModelTest {
        val viewModel = viewModel()
        viewModel.selectInstrument(Instrument.Cello)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(Instrument.Cello, state.instrument)
        assertEquals("cello-standard", state.preset.id)
        assertEquals(2, state.presets.size)
        assertEquals(Instrument.Cello, preferences.snapshot.first().instrument)
        assertEquals("cello-standard", preferences.snapshot.first().presetId)
        assertEquals(AudioState.Idle, state.audio)
    }

    @Test
    fun sevenStringsListSevenCards() = viewModelTest {
        val viewModel = viewModel()
        viewModel.selectGuitarStrings(GuitarStringCount.Seven)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals("guitar7-standard", state.preset.id)
        assertEquals(listOf("VII", "VI", "V", "IV", "III", "II", "I"), state.strings.map { it.numeral })
        viewModel.selectPreset("guitar7-drop-a")
        runCurrent()
        assertEquals("A1 E2 A2 D3 G3 B3 E4", viewModel.uiState.value.preset.summary)
        assertEquals("guitar7-drop-a", preferences.snapshot.first().presetId)
        viewModel.selectPreset("cello-standard")
        runCurrent()
        assertEquals("guitar7-drop-a", viewModel.uiState.value.preset.id)
    }

    @Test
    fun loopToggleAndModePersist() = viewModelTest {
        val viewModel = viewModel()
        viewModel.setLoopTone(false)
        viewModel.selectMode(TunerMode.ByEar)
        runCurrent()
        assertFalse(preferences.snapshot.first().loopTone)
        assertEquals(TunerMode.ByEar, preferences.snapshot.first().mode)
    }

    @Test
    fun aStoredPresetOfAnotherInstrumentFallsBackToTheDefault() = viewModelTest {
        preferences = FakeTunerPreferences(TunerPreferencesSnapshot(instrument = Instrument.Cello, presetId = "guitar6-drop-d"))
        val viewModel = viewModel()
        show(viewModel)
        assertEquals("cello-standard", viewModel.uiState.value.preset.id)
    }

    @Test
    fun changingTheInstrumentStopsAPlayingTone() = viewModelTest {
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertEquals(AudioState.Tone, viewModel.uiState.value.audio)
        viewModel.selectInstrument(Instrument.Cello)
        runCurrent()
        assertEquals("stopTone", audio.calls.last())
        assertNull(viewModel.uiState.value.audition)
        assertEquals(AudioState.Idle, viewModel.uiState.value.audio)
    }

    // --- Auditions (US3) ---------------------------------------------------------------------------------------------

    @Test
    fun auditionSoundsTheStringAndShowsToneOut() = viewModelTest {
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertEquals("startTone(82.41, 0)", audio.calls.last())
        assertEquals(AuditionState(0, true, ReferenceSound.Synth(Note.parse("E2").frequencyHz())), viewModel.uiState.value.audition)
        assertEquals(AudioState.Tone, viewModel.uiState.value.audio)
        assertEquals(AudioStatePill.ToneOut, viewModel.uiState.value.pill)
        viewModel.audition(1)
        runCurrent()
        assertEquals(listOf("stopTone", "startTone(110.0, 0)"), audio.calls.takeLast(2))
        viewModel.stopAudition()
        runCurrent()
        assertEquals("stopTone", audio.calls.last())
        assertNull(viewModel.uiState.value.audition)
    }

    @Test
    fun everyStopConditionEndsTheTone() = viewModelTest {
        val viewModel = viewModel()
        enterByEar(viewModel)
        val stops = listOf<(TunerViewModel) -> Unit>(
            { it.selectMode(TunerMode.AutoListen) },
            { it.screenVisible(false) },
            { it.foreground(false) },
            { it.selectPreset("guitar6-drop-d") },
        )
        for ((index, stop) in stops.withIndex()) {
            viewModel.selectMode(TunerMode.ByEar)
            viewModel.screenVisible(true)
            viewModel.foreground(true)
            runCurrent()
            viewModel.audition(0)
            runCurrent()
            assertEquals(AudioState.Tone, viewModel.uiState.value.audio, "before stop $index")
            val before = audio.calls.size
            stop(viewModel)
            runCurrent()
            // Switching to Auto Listen also starts listening afterwards, so the stop is checked, not the last call.
            assertTrue("stopTone" in audio.calls.drop(before), "stop $index did not stop the tone: ${audio.calls.drop(before)}")
            assertNull(viewModel.uiState.value.audition, "stop $index")
        }
    }

    @Test
    fun aOneShotThatEndedClearsTheAudition() = viewModelTest {
        val viewModel = viewModel()
        viewModel.setLoopTone(false)
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertEquals("startTone(82.41, 2000)", audio.calls.last())
        audio.audioState = AudioState.Idle
        advanceTimeBy(150)
        assertNull(viewModel.uiState.value.audition)
        assertEquals(AudioState.Idle, viewModel.uiState.value.audio)
    }

    @Test
    fun aRecordingIsLoadedOnceAndPlayedAsASample() = viewModelTest {
        recordings = mapOf("files/tuner/guitar-E2.wav" to byteArrayOf(1, 2, 3))
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertEquals(listOf("loadSample", "playSample(1, true)"), audio.calls.takeLast(2))
        viewModel.stopAudition()
        viewModel.audition(0)
        runCurrent()
        assertEquals(listOf("stopTone", "playSample(1, true)"), audio.calls.takeLast(2))
        assertEquals(1, audio.calls.count { it == "loadSample" })
        viewModel.clear()
        assertEquals("unloadSample", audio.calls.last())
    }

    @Test
    fun aRecordingThatCannotBeDecodedLeavesNoAudition() = viewModelTest {
        // The fake engine decodes nothing from empty bytes, like the real one from bytes that are not a WAV.
        recordings = mapOf("files/tuner/guitar-E2.wav" to byteArrayOf())
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertNull(viewModel.uiState.value.audition)
        assertEquals(AudioState.Idle, viewModel.uiState.value.audio)
        assertFalse(audio.calls.any { it.startsWith("playSample") }, audio.calls.toString())
    }

    @Test
    fun aLaterAuditionWinsOverOneStillDecoding() = viewModelTest {
        recordings = mapOf("files/tuner/guitar-E2.wav" to byteArrayOf(1, 2, 3))
        val decode = HeldDispatcher()
        val viewModel = viewModel(decodeDispatcher = decode)
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertNull(viewModel.uiState.value.audition, "the recording is still decoding")
        viewModel.audition(1)
        runCurrent()
        assertEquals("startTone(110.0, 0)", audio.calls.last())
        decode.release()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.audition?.stringIndex, "the earlier audition overtook the later one")
        assertFalse(audio.calls.any { it.startsWith("playSample") }, "the stale decode sounded its sample: ${audio.calls}")
        // The decode itself is kept: the next audition of that string plays without loading again.
        viewModel.audition(0)
        runCurrent()
        assertEquals("playSample(1, true)", audio.calls.last())
        assertEquals(1, audio.calls.count { it == "loadSample" })
    }

    @Test
    fun backgroundingWhileARecordingDecodesNeverSoundsItLater() = viewModelTest {
        recordings = mapOf("files/tuner/guitar-E2.wav" to byteArrayOf(1, 2, 3))
        val decode = HeldDispatcher()
        val viewModel = viewModel(decodeDispatcher = decode)
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        viewModel.foreground(false)
        runCurrent()
        decode.release()
        runCurrent()
        assertNull(viewModel.uiState.value.audition, "a cleared audition came back once the decode finished")
        viewModel.foreground(true)
        runCurrent()
        assertNull(viewModel.uiState.value.audition)
        assertFalse(audio.calls.any { it.startsWith("playSample") || it.startsWith("startTone") }, "a tone started after backgrounding: ${audio.calls}")
    }

    @Test
    fun aLostOutputRouteStopsTheTone() = viewModelTest {
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        audio.emit(AudioEvent.OutputRouteLost)
        runCurrent()
        assertEquals("stopTone", audio.calls.last())
        assertNull(viewModel.uiState.value.audition)
    }

    // --- Loop toggle (US4) -------------------------------------------------------------------------------------------

    @Test
    fun theLoopToggleReArmsASoundingToneWithoutAStop() = viewModelTest {
        val viewModel = viewModel()
        viewModel.setLoopTone(false)
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        assertEquals("startTone(82.41, 2000)", audio.calls.last())
        val before = audio.calls.size
        viewModel.setLoopTone(true)
        runCurrent()
        assertEquals(listOf("startTone(82.41, 0)"), audio.calls.drop(before))
        assertEquals(true, viewModel.uiState.value.audition?.loop)
        viewModel.setLoopTone(false)
        runCurrent()
        assertEquals("startTone(82.41, 2000)", audio.calls.last())
        assertFalse(audio.calls.drop(before).contains("stopTone"))
        assertFalse(preferences.snapshot.first().loopTone)
    }

    // --- Permission and interruptions (US5) ---------------------------------------------------------------------------

    @Test
    fun deniedCanAskRequestsOnlyFromTheButton() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.DeniedCanAsk)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(0, permission.requestCount)
        assertTrue(viewModel.uiState.value.showDeniedState)
        permission.requestResults += MicrophonePermissionStatus.Granted
        viewModel.requestMicrophone()
        runCurrent()
        assertEquals(1, permission.requestCount)
        assertEquals(listOf("startListening"), audio.calls)
    }

    @Test
    fun deniedForGoodOpensTheSettings() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.DeniedOpenSettings)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.requestMicrophone()
        runCurrent()
        assertEquals(1, permission.openSettingsCount)
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun aGrantFromTheSettingsStartsListeningAndARevokeStopsIt() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.DeniedOpenSettings)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        permission.set(MicrophonePermissionStatus.Granted)
        runCurrent()
        assertEquals(listOf("startListening"), audio.calls)
        assertEquals(0, permission.requestCount)
        permission.set(MicrophonePermissionStatus.DeniedOpenSettings)
        runCurrent()
        assertEquals("stopListening", audio.calls.last())
        assertTrue(viewModel.uiState.value.showDeniedState)
    }

    @Test
    fun theFirstRequestHappensOncePerVisibility() = viewModelTest {
        permission = FakeMicrophonePermission(MicrophonePermissionStatus.NotDetermined)
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.foreground(true)
        runCurrent()
        assertEquals(1, permission.requestCount)
    }

    @Test
    fun interruptionsStopAndResumeListening() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        audio.emit(AudioEvent.Interrupted)
        runCurrent()
        assertEquals("stopListening", audio.calls.last())
        assertTrue(viewModel.uiState.value.interrupted)
        assertNull(viewModel.uiState.value.pill)
        audio.emit(AudioEvent.InterruptionEnded)
        runCurrent()
        assertEquals("startListening", audio.calls.last())
        assertFalse(viewModel.uiState.value.interrupted)
        audio.emit(AudioEvent.DeviceLost)
        runCurrent()
        assertEquals(listOf("stopListening", "startListening"), audio.calls.takeLast(2))
    }

    @Test
    fun anInterruptionEndsAToneForGood() = viewModelTest {
        val viewModel = viewModel()
        enterByEar(viewModel)
        viewModel.audition(0)
        runCurrent()
        audio.emit(AudioEvent.Interrupted)
        runCurrent()
        assertEquals("stopTone", audio.calls.last())
        assertNull(viewModel.uiState.value.audition)
        audio.emit(AudioEvent.InterruptionEnded)
        runCurrent()
        assertEquals("stopTone", audio.calls.last())
    }

    // --- Noise gate (feature 006) ----------------------------------------------------------------------------------------

    @Test
    fun movingTheNoiseGateReachesTheEngineAtOnceAndWritesNothing() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        val gate = NoiseGate.ofLevelDb(-50)!!
        viewModel.setNoiseGate(gate)
        assertEquals(gate, viewModel.uiState.value.noiseGate)
        assertEquals("setNoiseGate(-50.0)", audio.calls.last())
        assertEquals(-50f, audio.noiseGateDb)
        runCurrent()
        assertEquals(TunerPreferencesSnapshot(), preferences.snapshot.first(), "a move of the handle must not write")

        // An unchanged value does nothing.
        val calls = audio.calls.size
        viewModel.setNoiseGate(gate)
        assertEquals(calls, audio.calls.size)

        // Listening was started once and never stopped or restarted by the moves (spec FR-003).
        viewModel.setNoiseGate(NoiseGate.Off)
        assertEquals("setNoiseGate(off)", audio.calls.last())
        assertEquals(1, audio.calls.count { it == "startListening" })
        assertEquals(0, audio.calls.count { it == "stopListening" })
        assertEquals(AudioState.Listening, viewModel.uiState.value.audio)
    }

    @Test
    fun theNoiseGateStaysThroughModesVisibilityBackgroundingAndInterruptions() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        val gate = NoiseGate.ofLevelDb(-40)!!
        viewModel.setNoiseGate(gate)
        viewModel.selectMode(TunerMode.ByEar)
        runCurrent()
        viewModel.selectMode(TunerMode.AutoListen)
        runCurrent()
        viewModel.screenVisible(false)
        runCurrent()
        viewModel.screenVisible(true)
        runCurrent()
        viewModel.foreground(false)
        runCurrent()
        viewModel.foreground(true)
        runCurrent()
        audio.emit(AudioEvent.Interrupted)
        runCurrent()
        audio.emit(AudioEvent.InterruptionEnded)
        runCurrent()

        assertEquals(gate, viewModel.uiState.value.noiseGate)
        assertEquals(-40f, audio.noiseGateDb)
        assertEquals("startListening", audio.calls.last())
        assertTrue(audio.calls.count { it == "startListening" } >= 5, "listening restarted ${audio.calls.count { it == "startListening" }} times")
        // The engine keeps the gate across every stop and start: nothing after the move touches it again.
        val moved = audio.calls.lastIndexOf("setNoiseGate(-40.0)")
        assertTrue(audio.calls.drop(moved + 1).none { it.startsWith("setNoiseGate") }, "calls after the move: ${audio.calls.drop(moved + 1)}")
    }

    @Test
    fun aRememberedNoiseGateReachesTheEngineBeforeListeningStarts() = viewModelTest {
        preferences = FakeTunerPreferences(TunerPreferencesSnapshot(noiseGate = NoiseGate.ofLevelDb(-50)!!))
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(NoiseGate.ofLevelDb(-50)!!, viewModel.uiState.value.noiseGate)
        assertEquals(listOf("setNoiseGate(-50.0)", "startListening"), audio.calls)
    }

    @Test
    fun aFreshInstallLoadsTheGateOffAndLeavesTheEngineAlone() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        assertEquals(NoiseGate.Off, viewModel.uiState.value.noiseGate)
        assertEquals(listOf("startListening"), audio.calls)
    }

    @Test
    fun settlingWritesTheNoiseGateAndOnlyThen() = viewModelTest {
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.setNoiseGate(NoiseGate.ofLevelDb(-60)!!)
        viewModel.setNoiseGate(NoiseGate.ofLevelDb(-58)!!)
        runCurrent()
        assertEquals(NoiseGate.Off, preferences.snapshot.first().noiseGate)
        viewModel.noiseGateSettled()
        runCurrent()
        assertEquals(-58, preferences.snapshot.first().noiseGate.levelDb)
        // The other remembered choices are untouched by the write.
        assertEquals(TunerPreferencesSnapshot().copy(noiseGate = NoiseGate.ofLevelDb(-58)!!), preferences.snapshot.first())
    }

    @Test
    fun clearingTakesTheGateOutOfTheEngine() = viewModelTest {
        // The gate lives in the engine for the life of the process, the ViewModel for the life of its entry: a gate
        // this ViewModel set must not outlive it, or the next entry would show "Off" over a gated engine.
        val viewModel = viewModel()
        enterAutoListen(viewModel)
        viewModel.setNoiseGate(NoiseGate.ofLevelDb(-50)!!)
        viewModel.clear()
        created = null
        assertEquals(listOf("stopListening", "setNoiseGate(off)"), audio.calls.takeLast(2))
        assertNull(audio.noiseGateDb)
    }
}
