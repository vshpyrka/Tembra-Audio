package audio.tembra.ui.session

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.DeleteTrack
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.RenameTrack
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetSoloed
import audio.tembra.core.project.SetTempo
import audio.tembra.core.project.Percent
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.ImportRefusal
import audio.tembra.data.projects.ImportResult
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.PickedFile
import audio.tembra.ui.drop.SkipReason
import audio.tembra.ui.drop.SkippedItem
import audio.tembra.ui.drop.SessionProjectFromAudio
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.AudioState
import audio.tembra.engine.PlayResult
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session against the fakes (contracts/project-session.md; tasks T038 and T087). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drumsTrack = demo.tracks[0].id
    private val bassTrack = demo.tracks[1].id
    private val drumsAsset = DemoProject.BUNDLED_ASSET_IDS.getValue("drums")
    private val overview = WaveformOverview(channels = 1, frames = 512, completedBaseBuckets = 2, levels = emptyList())
    private val engine = FakeAudioEngine()
    private val host = FakeBackgroundPlaybackHost()
    private val preferences = FakeMetronomePreferences()
    private val repository = FakeProjectRepository(initial = listOf(demo))
    private val session = newSession(repository)

    private fun newSession(repository: FakeProjectRepository) = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = preferences,
        host = host,
        scope = scope,
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )

    @AfterTest
    fun tearDown() = scope.cancel()

    private suspend fun TestScope.openDemo() {
        session.start()
        runCurrent()
        session.open(demo.id)
        runCurrent()
    }

    private suspend fun TestScope.playDemo() {
        openDemo()
        session.play()
        runCurrent()
        assertEquals(PlaybackState.Playing, session.state.value.playback)
    }

    private fun audioPath(assetId: AudioAssetId): String = "/fake/${demo.id.value}/audio/${assetId.sha256}.wav"

    private fun callNames(): List<String> = engine.calls.map { it.substringBefore('(') }

    // --- Open --------------------------------------------------------------------------------------------------------

    /** Leaving the Timeline hands the device over (the tuner and the metronome need an idle engine); returning takes it back at the position (spec FR-008a). */
    @Test
    fun leavingTheTransportHostClosesTheTransportAndReturningReopensItAtThePosition() = runTest(dispatcher) {
        openDemo()
        session.seek(Beats(8.0))
        advanceUntilIdle()
        val before = engine.calls.size
        session.transportHostVisible(false)
        assertEquals(listOf("closeTransport"), callNames().drop(before), "the hand-over is synchronous")
        session.transportHostVisible(true)
        advanceUntilIdle()
        assertEquals(
            listOf("closeTransport", "openTransport", "setTempo", "setMaster", "setArrangement", "setGrid", "selectSound", "setMetronome", "seek"),
            callNames().drop(before),
        )
        assertEquals("seek(8.0)", engine.calls.last())
        assertEquals(PlaybackState.Stopped(Beats(8.0)), session.state.value.playback)
    }

    /** Play right after returning queues behind the reopen: the device is held open by the fake until the test lets go. */
    @Test
    fun playRightAfterReturningWaitsForTheTransportToBeBack() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent() // the attach is suspended inside the open
        val before = engine.calls.size
        session.play()
        runCurrent()
        assertFalse("play" in callNames().drop(before), "play ran before the transport was back")
        engine.openTransportGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("setTempo", "setMaster", "setArrangement", "setGrid", "selectSound", "setMetronome", "seek", "play"), callNames().drop(before))
        assertEquals(PlaybackState.Playing, session.state.value.playback)
        session.pause()
        advanceUntilIdle()
    }

    /** A seek while another screen holds the device moves the stored position only; the return pushes it. */
    @Test
    fun aSeekWhileDetachedStaysAwayFromTheEngine() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        val before = engine.calls.size
        session.seek(Beats(12.0))
        advanceUntilIdle()
        assertEquals(emptyList(), callNames().drop(before), "nothing reaches the closed transport")
        assertEquals(PlaybackState.Stopped(Beats(12.0)), session.state.value.playback)
        session.transportHostVisible(true)
        advanceUntilIdle()
        assertEquals("seek(12.0)", engine.calls.last())
    }

    /** A leave that lands while the device is still opening: the open finishes and is closed again at once. */
    @Test
    fun leavingWhileTheTransportReopensClosesItAgain() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent()
        val before = engine.calls.size
        session.transportHostVisible(false)
        engine.openTransportGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("closeTransport"), callNames().drop(before), "the late open is closed and nothing is pushed")
        assertEquals(AudioState.Idle, engine.audioState, "the engine is free for the other screen")
    }

    /** The screen that held the device lets go as it leaves: a busy engine is retried until the open succeeds. */
    @Test
    fun aBusyEngineIsRetriedUntilTheOtherScreenLetsGo() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        engine.transportBusyFor = 3
        val before = engine.calls.size
        session.transportHostVisible(true)
        advanceUntilIdle()
        val names = callNames().drop(before)
        assertEquals(4, names.count { it == "openTransport" }, "three busy answers, then the open: $names")
        assertTrue("setArrangement" in names && names.last() == "seek", "the transport is bound after the retries: $names")
        assertEquals(AudioState.Transport, engine.audioState)
    }

    /** When the retries give up, play binds the transport in full rather than opening a bare one. */
    @Test
    fun playAfterAFailedReattachBindsTheTransportFully() = runTest(dispatcher) {
        openDemo()
        session.seek(Beats(8.0))
        advanceUntilIdle()
        session.transportHostVisible(false)
        engine.transportBusyFor = 60
        session.transportHostVisible(true)
        advanceUntilIdle()
        assertEquals(AudioState.Idle, engine.audioState, "the attach gave up")
        engine.transportBusyFor = 0
        val before = engine.calls.size
        session.play()
        advanceUntilIdle()
        assertEquals(listOf("openTransport", "setTempo", "setMaster", "setArrangement", "setGrid", "selectSound", "setMetronome", "seek", "play"), callNames().drop(before))
        assertEquals("seek(8.0)", engine.calls.dropLast(1).last(), "play resumes where the user left, not at the start")
        assertEquals(PlaybackState.Playing, session.state.value.playback)
        session.pause()
        advanceUntilIdle()
    }

    /** A tap on Tuner or Metronome while the start-up open runs leaves the device to that screen; the return takes it back. */
    @Test
    fun aTapOnAnotherDestinationDuringStartupLeavesTheDeviceToIt() = runTest(dispatcher) {
        session.start()
        runCurrent()
        engine.openTransportGate = CompletableDeferred()
        val opening = launch { session.open(demo.id) }
        runCurrent() // the start-up open is in flight
        session.transportHostVisible(false)
        engine.openTransportGate?.complete(Unit)
        opening.join()
        runCurrent()
        assertEquals(listOf("openTransport", "closeTransport"), callNames().filter { it.endsWith("Transport") }, "the late open is closed again")
        assertEquals(AudioState.Idle, engine.audioState, "the device is free for the other screen")
        assertEquals(demo, session.state.value.project)
        val before = engine.calls.size
        session.transportHostVisible(true)
        advanceUntilIdle()
        assertEquals(listOf("openTransport", "setTempo", "setMaster", "setArrangement", "setGrid", "selectSound", "setMetronome", "seek"), callNames().drop(before))
    }

    /** Leaving and returning while one open is in flight: that open is kept, no second one starts, the position lands. */
    @Test
    fun leavingAndReturningDuringOneOpenKeepsThatOpenAndThePosition() = runTest(dispatcher) {
        openDemo()
        session.seek(Beats(8.0))
        advanceUntilIdle()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent() // the attach is inside its open
        val before = engine.calls.size
        session.transportHostVisible(false)
        session.transportHostVisible(true)
        runCurrent()
        engine.openTransportGate?.complete(Unit)
        advanceUntilIdle()
        val names = callNames().drop(before)
        assertEquals(0, names.count { it == "openTransport" }, "no second open while one is in flight: $names")
        assertEquals("seek(8.0)", engine.calls.last())
        assertEquals(AudioState.Transport, engine.audioState)
        session.transportHostVisible(false)
        assertEquals("closeTransport", engine.calls.last(), "the next leave hands the device over")
        assertEquals(AudioState.Idle, engine.audioState)
    }

    /** A pause while play waits for the transport: the stored position stays, nothing plays, the host is let go. */
    @Test
    fun pauseWhilePlayWaitsForTheTransportStartsNothingAndKeepsThePosition() = runTest(dispatcher) {
        openDemo()
        session.seek(Beats(8.0))
        advanceUntilIdle()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent()
        session.play()
        runCurrent()
        assertEquals(PlaybackState.Starting(Beats(8.0)), session.state.value.playback)
        session.pause()
        runCurrent()
        assertEquals(PlaybackState.Stopped(Beats(8.0)), session.state.value.playback, "the position the user left, not the engine's")
        val before = engine.calls.size
        engine.openTransportGate?.complete(Unit)
        advanceUntilIdle()
        assertFalse("play" in callNames().drop(before), "nothing plays after the pause")
        assertFalse(host.alive, "the playback host is not kept alive")
        assertEquals("seek(8.0)", engine.calls.last(), "the bind still lands the stored position")
    }

    /** Play, pause, play while the transport comes back: one start, and the engine plays when the state says so. */
    @Test
    fun playPausePlayWhileTheTransportComesBackEndsPlaying() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent()
        session.play()
        runCurrent()
        session.pause()
        runCurrent()
        session.play()
        runCurrent()
        val before = engine.calls.size
        engine.openTransportGate?.complete(Unit)
        advanceUntilIdle()
        val names = callNames().drop(before)
        assertEquals(1, names.count { it == "play" }, "one start: $names")
        assertEquals(0, names.count { it == "pause" }, "no pause after it: $names")
        assertEquals(PlaybackState.Playing, session.state.value.playback)
        assertTrue(engine.transportPlaying, "the engine plays")
        assertTrue(host.alive)
        session.pause()
        advanceUntilIdle()
    }

    /** Opening a project while the transport reattaches: that open lands, closes with the old project, and the new project opens the device once. */
    @Test
    fun openingAProjectWhileTheTransportReattachesEndsWithOneOpenTransport() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        engine.openTransportGate = CompletableDeferred()
        session.transportHostVisible(true)
        runCurrent() // the attach is inside its open
        val before = engine.calls.size
        val reopening = launch { session.open(demo.id) }
        runCurrent() // the close waits for the open in flight
        engine.openTransportGate?.complete(Unit)
        reopening.join()
        advanceUntilIdle()
        val transport = callNames().drop(before).filter { it.endsWith("Transport") }
        assertEquals(listOf("closeTransport", "openTransport"), transport, "closed with the old project, opened once for the new: $transport")
        assertEquals(AudioState.Transport, engine.audioState)
        session.transportHostVisible(false)
        assertEquals("closeTransport", engine.calls.last(), "the session still knows it holds the device")
        assertEquals(AudioState.Idle, engine.audioState)
    }

    /** The click settings changed on the Metronome destination stay away from the engine while it belongs to that screen. */
    @Test
    fun metronomeSettingsChangedWhileDetachedStayAwayFromTheEngine() = runTest(dispatcher) {
        openDemo()
        session.transportHostVisible(false)
        val before = engine.calls.size
        preferences.update { it.copy(settings = it.settings.copy(subdivision = Subdivision.Two)) }
        advanceUntilIdle()
        assertEquals(emptyList(), callNames().drop(before), "nothing reaches the engine while detached")
        session.transportHostVisible(true)
        advanceUntilIdle()
        assertTrue(engine.calls.drop(before).any { it.startsWith("setGrid(") && "ticks=2" in it }, "the attach applies the changed click settings: ${engine.calls.drop(before)}")
    }

    @Test
    fun openBindsTheEngineInTheContractsOrderThenRecordsTheOpenProject() = runTest(dispatcher) {
        openDemo()
        assertEquals(
            listOf("openTransport") + List(4) { "openClipSource" } + listOf("setTempo", "setMaster", "setArrangement", "setGrid", "selectSound", "setMetronome"),
            callNames(),
        )
        assertEquals("setTempo(124, 4/4)", engine.calls[5])
        assertEquals("setMaster(0.8)", engine.calls[6])
        assertEquals("setArrangement(tracks=4,clips=5)", engine.calls[7])
        assertEquals("setMetronome(false)", engine.calls[10])
        assertEquals(listOf("open(${demo.id.value})", "setOpen(${demo.id.value})"), repository.calls.filter { it.startsWith("open(") || it.startsWith("setOpen(") })
        val state = session.state.value
        assertEquals(demo, state.project)
        assertEquals(PlaybackState.Stopped(Beats.Zero), state.playback)
        assertFalse(state.canUndo)
        assertEquals(4, state.assetsReady.size, "every asset reports its overview status")
        assertTrue(state.assetsReady.values.all { it == OverviewStatus.Ready })
        assertEquals(4, state.overviews.size, "every readable overview is published")
    }

    @Test
    fun aMissingFileLandsInMissingAssetsAndIsReported() = runTest(dispatcher) {
        repository.missingAssets += drumsAsset
        val received = collectMessages()
        openDemo()
        assertEquals(setOf(drumsAsset), session.state.value.missingAssets)
        assertEquals(3, callNames().count { it == "openClipSource" })
        assertEquals("setArrangement(tracks=4,clips=4)", engine.calls.first { it.startsWith("setArrangement") }, "the clip without a source is left out")
        assertEquals(listOf(SessionMessage.AssetsMissing(1)), received)
    }

    @Test
    fun anUndecodableFileLandsInMissingAssetsToo() = runTest(dispatcher) {
        engine.clipSourceFailures += audioPath(drumsAsset)
        openDemo()
        assertEquals(setOf(drumsAsset), session.state.value.missingAssets)
    }

    @Test
    fun aDocumentThatCannotBeOpenedLeavesNoProjectAndSaysWhy() = runTest(dispatcher) {
        val received = collectMessages()
        session.start()
        session.open(audio.tembra.core.project.ProjectId("nope"))
        runCurrent()
        assertNull(session.state.value.project)
        assertEquals("The project could not be found", session.state.value.unavailable)
        assertIs<SessionMessage.DocumentUnavailable>(received.single())
    }

    @Test
    fun openingAnotherProjectPausesFirstThenRebindsTheEngine() = runTest(dispatcher) {
        playDemo()
        val other = repository.create("Other")
        session.open(other.id)
        runCurrent()
        val calls = engine.calls
        val pause = calls.indexOf("pause")
        val close = calls.indexOf("closeTransport")
        val reopen = calls.lastIndexOf("openTransport")
        assertTrue(pause in 1..<close && close < reopen, "pause, close, reopen: $calls")
        assertEquals(4, calls.count { it.startsWith("closeClipSource(") })
        assertEquals(other, session.state.value.project)
        assertEquals(PlaybackState.Stopped(Beats.Zero), session.state.value.playback)
        assertEquals("setOpen(${other.id.value})", repository.calls.last { it.startsWith("setOpen(") })
    }

    // --- Edits -------------------------------------------------------------------------------------------------------

    @Test
    fun aGainEditUsesTheTrackFastPathOnly() = runTest(dispatcher) {
        openDemo()
        val before = engine.calls.size
        assertNull(session.edit(SetGain(drumsTrack, GainDb(-6f))))
        assertEquals(listOf("setTrack(0,0.501,true)"), engine.calls.drop(before))
        assertTrue(session.state.value.canUndo)
    }

    @Test
    fun aSoloChangeUpdatesEveryTrack() = runTest(dispatcher) {
        openDemo()
        val before = engine.calls.size
        session.edit(SetSoloed(bassTrack, true))
        assertEquals(listOf("setTrack(0,1.0,false)", "setTrack(1,1.0,true)", "setTrack(2,1.0,false)", "setTrack(3,1.0,false)"), engine.calls.drop(before))
    }

    @Test
    fun aTempoEditRePushesTheTempoTheArrangementAndTheClickGrid() = runTest(dispatcher) {
        openDemo()
        val before = engine.calls.size
        session.edit(SetTempo(Tempo(100)))
        assertEquals(listOf("setTempo", "setArrangement", "setGrid", "selectSound"), engine.calls.drop(before).map { it.substringBefore('(') })
        assertEquals("setTempo(100, 4/4)", engine.calls[before])
    }

    @Test
    fun undoAndRedoRePushWhatDiffers() = runTest(dispatcher) {
        openDemo()
        session.edit(SetTempo(Tempo(100)))
        val before = engine.calls.size
        session.undo()
        assertEquals(listOf("setTempo(124, 4/4)", "setGrid", "selectSound", "setArrangement(tracks=4,clips=5)"), engine.calls.drop(before).map { if (it.startsWith("setTempo") || it.startsWith("setArrangement")) it else it.substringBefore('(') })
        assertEquals(Tempo(124), session.state.value.project?.tempo)
        assertTrue(session.state.value.canRedo)
        val beforeRedo = engine.calls.size
        session.redo()
        assertEquals("setTempo(100, 4/4)", engine.calls[beforeRedo])
        assertEquals(Tempo(100), session.state.value.project?.tempo)
    }

    @Test
    fun aRunOfGainChangesIsOneUndoStepUntilTheGroupCloses() = runTest(dispatcher) {
        openDemo()
        session.edit(SetGain(drumsTrack, GainDb(-1f)))
        session.edit(SetGain(drumsTrack, GainDb(-2f)))
        session.edit(SetGain(drumsTrack, GainDb(-3f)))
        session.closeEditGroup()
        session.edit(SetMasterVolume(Percent(50)))
        session.undo()
        assertEquals(Percent(80), session.state.value.project?.masterVolume)
        session.undo()
        assertEquals(GainDb.Unity, session.state.value.project?.tracks?.first()?.gainDb, "the three gain changes undo as one")
        assertFalse(session.state.value.canUndo)
    }

    @Test
    fun theMetronomeToggleReachesTheEngineAndIsNotAnUndoStep() = runTest(dispatcher) {
        openDemo()
        session.setMetronome(true)
        assertEquals("setMetronome(true)", engine.calls.last())
        assertTrue(session.state.value.metronomeOn)
        assertFalse(session.state.value.canUndo)
        session.edit(SetTempo(Tempo(100)))
        session.undo()
        assertTrue(session.state.value.project?.metronomeOn == true, "undo leaves the toggle as it is")
    }

    // --- Import and retirement ---------------------------------------------------------------------------------------

    @Test
    fun importOpensTheSourceBeforeTheArrangementAndAddsTheTrack() = runTest(dispatcher) {
        openDemo()
        val id = AudioAssetId("ff".repeat(32))
        val asset = AudioAsset("lead.wav", AudioFormat.Wav, channels = 2, sampleRate = 48_000, frames = 48_000, byteSize = 192_044)
        repository.importResults += ImportResult.Accepted(id, asset, ClipTemplate.forAsset(id, asset), true, "/fake/audio/lead.wav", "/fake/cache/lead.peaks")
        val before = engine.calls.size
        session.importAudio(listOf(PickedFile("lead.wav") { Buffer() }), ImportTarget.NewTracks(Beats.Zero))
        runCurrent()
        assertEquals(listOf("openClipSource(/fake/audio/lead.wav)", "setArrangement(tracks=5,clips=6)"), engine.calls.drop(before))
        val project = session.state.value.project!!
        assertEquals("lead", project.tracks.last().name)
        assertEquals(OverviewStatus.Ready, session.state.value.assetsReady[id])
    }

    @Test
    fun refusedFilesArriveAsOneMessage() = runTest(dispatcher) {
        openDemo()
        val received = collectMessages()
        repository.importResults += ImportResult.Refused("a.ogg", ImportRefusal.UnsupportedFormat)
        repository.importResults += ImportResult.Refused("b.wav", ImportRefusal.TooLong)
        session.importAudio(listOf(PickedFile("a.ogg") { Buffer() }, PickedFile("b.wav") { Buffer() }), ImportTarget.Track(drumsTrack, Beats.Zero))
        runCurrent()
        assertEquals(listOf(SessionMessage.ImportRefused(listOf("a.ogg" to "not a WAV, FLAC or MP3 file", "b.wav" to "longer than 30 minutes"))), received)
    }

    @Test
    fun itemsADropLeftOutJoinTheRefusalsInOneMessage() = runTest(dispatcher) {
        // Feature 011 (spec FR-026): the drop's skipped items first, in drop order, then the importer's refusals.
        openDemo()
        val received = collectMessages()
        repository.importResults += ImportResult.Refused("a.ogg", ImportRefusal.UnsupportedFormat)
        val skipped = listOf(SkippedItem("notes.txt", SkipReason.NotAudio), SkippedItem("Stems", SkipReason.UnreadableFolder), SkippedItem("Old", SkipReason.NestedFolder))
        session.importAudio(listOf(PickedFile("a.ogg") { Buffer() }), ImportTarget.NewTracks(Beats.Zero), skipped)
        runCurrent()
        val expected = listOf(
            "notes.txt" to "not an audio file",
            "Stems" to "a folder this device does not let the app open",
            "Old" to "a folder inside the dropped folder",
            "a.ogg" to "not a WAV, FLAC or MP3 file",
        )
        assertEquals(listOf(SessionMessage.ImportRefused(expected)), received)
    }

    @Test
    fun anAssetNoSnapshotReferencesIsClosedAndRetiredWithTheNextSave() = runTest(dispatcher) {
        openDemo()
        session.edit(DeleteTrack(drumsTrack))
        assertTrue(engine.calls.none { it.startsWith("closeClipSource") }, "the deleting snapshot still references the asset")
        repeat(50) { session.edit(RenameTrack(bassTrack, "Bass $it")) }
        assertEquals("closeClipSource(1)", engine.calls.last { it.startsWith("closeClipSource") })
        assertNull(session.state.value.assetsReady[drumsAsset])
        advanceUntilIdle()
        assertEquals(setOf(drumsAsset), repository.retired.last())
    }

    // --- Playback ----------------------------------------------------------------------------------------------------

    @Test
    fun playKeepsTheHostAliveThenStartsTheEngine() = runTest(dispatcher) {
        openDemo()
        session.play()
        assertIs<PlaybackState.Starting>(session.state.value.playback)
        runCurrent()
        assertEquals(PlaybackState.Playing, session.state.value.playback)
        assertEquals(1, host.keepAliveCount)
        assertEquals("Playing", host.notification?.title)
        assertEquals(DemoProject.NAME, host.notification?.subtitle)
        assertEquals("play", engine.calls.last())
    }

    @Test
    fun aFailedStartReleasesTheHostAndReportsIt() = runTest(dispatcher) {
        openDemo()
        val received = collectMessages()
        engine.failNextPlay = PlayResult.Failed(-1)
        session.play()
        runCurrent()
        assertEquals(PlaybackState.Stopped(Beats.Zero), session.state.value.playback)
        assertEquals(1, host.releaseCount)
        assertEquals(listOf(SessionMessage.StartFailed), received)
    }

    @Test
    fun pauseStopsTheEngineReleasesTheHostAndKeepsThePosition() = runTest(dispatcher) {
        playDemo()
        engine.nextTransport.positionBeats = 7.5
        session.pause()
        assertEquals(listOf("pause"), engine.calls.takeLast(1))
        assertEquals(1, host.releaseCount)
        assertEquals(PlaybackState.Stopped(Beats(7.5)), session.state.value.playback)
    }

    @Test
    fun anInterruptionPausesAndNeverResumes() = runTest(dispatcher) {
        playDemo()
        engine.emit(AudioEvent.Interrupted)
        runCurrent()
        assertIs<PlaybackState.Stopped>(session.state.value.playback)
        assertEquals(1, host.releaseCount)
        engine.emit(AudioEvent.InterruptionEnded)
        runCurrent()
        assertIs<PlaybackState.Stopped>(session.state.value.playback)
        assertEquals(1, engine.calls.count { it == "play" })
    }

    @Test
    fun aLostRouteAndAHostStopPauseAsWell() = runTest(dispatcher) {
        playDemo()
        engine.emit(AudioEvent.OutputRouteLost)
        runCurrent()
        assertIs<PlaybackState.Stopped>(session.state.value.playback)
        session.play()
        runCurrent()
        host.requestStop()
        runCurrent()
        assertIs<PlaybackState.Stopped>(session.state.value.playback)
        assertEquals(2, engine.calls.count { it == "pause" })
    }

    @Test
    fun seeksReachTheEngineAndTheLatestWinsWhileScrubbing() = runTest(dispatcher) {
        openDemo()
        session.seek(Beats(4.0))
        session.seek(Beats(5.0))
        session.seek(Beats(6.0))
        runCurrent()
        // The worker takes the first seek at once; the two that arrive while it runs conflate to the latest.
        assertEquals(listOf("seek(4.0)", "seek(6.0)"), engine.calls.filter { it.startsWith("seek") })
        assertEquals(PlaybackState.Stopped(Beats(6.0)), session.state.value.playback)
        session.skipToStart()
        runCurrent()
        assertEquals("seek(0.0)", engine.calls.last())
        session.seek(Beats(6.0))
        runCurrent()
        session.stepBar(1)
        runCurrent()
        assertEquals("seek(8.0)", engine.calls.last())
    }

    @Test
    fun theTelemetryReadsTheEngineOncePerFrame() = runTest(dispatcher) {
        openDemo()
        val into = TransportSnapshot()
        engine.nextTransport.positionBeats = 3.0
        session.transportTelemetry.read(10L, into)
        session.transportTelemetry.read(10L, into)
        assertEquals(1, engine.readTransportCount)
        assertEquals(3.0, into.positionBeats)
        session.transportTelemetry.read(20L, into)
        assertEquals(2, engine.readTransportCount)
    }

    // --- Stop-and-leave guard ----------------------------------------------------------------------------------------

    @Test
    fun confirmLeaveAsksAndStopsOnStopAndLeave() = runTest(dispatcher) {
        playDemo()
        assertTrue(session.isPlaying)
        val leave = async { session.confirmLeave() }
        runCurrent()
        val dialog = assertIs<DialogRequest.StopAndLeave>(session.dialog.value)
        dialog.answer(true)
        runCurrent()
        assertTrue(leave.await())
        assertNull(session.dialog.value)
        assertIs<PlaybackState.Stopped>(session.state.value.playback)
        assertEquals("pause", engine.calls.last())
    }

    @Test
    fun confirmLeaveKeepsPlayingOnKeepPlaying() = runTest(dispatcher) {
        playDemo()
        val leave = async { session.confirmLeave() }
        runCurrent()
        assertIs<DialogRequest.StopAndLeave>(session.dialog.value).answer(false)
        runCurrent()
        assertFalse(leave.await())
        assertEquals(PlaybackState.Playing, session.state.value.playback)
        assertTrue(engine.calls.none { it == "pause" })
    }

    @Test
    fun confirmLeaveNeedsNoDialogWhenStopped() = runTest(dispatcher) {
        openDemo()
        assertTrue(session.confirmLeave())
        assertNull(session.dialog.value)
    }

    // --- Autosave ----------------------------------------------------------------------------------------------------

    @Test
    fun editsSaveAfterHalfASecondOfQuiet() = runTest(dispatcher) {
        openDemo()
        session.edit(RenameTrack(drumsTrack, "Kick"))
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, saves())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, saves())
        assertEquals("Kick", repository.saved.getValue(demo.id).tracks.first().name)
        assertFalse(session.state.value.saving)
    }

    @Test
    fun continuousEditsSaveAtTheTwoSecondCap() = runTest(dispatcher) {
        openDemo()
        repeat(4) {
            session.edit(RenameTrack(drumsTrack, "Kick $it"))
            advanceTimeBy(400)
            runCurrent()
        }
        session.edit(RenameTrack(drumsTrack, "Kick 4"))
        advanceTimeBy(399)
        runCurrent()
        assertEquals(0, saves(), "edits every 400 ms keep the debounce open")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, saves(), "the first edit's cap at 2 s")
    }

    @Test
    fun flushSaveWritesAtOnceWhenTheAppBackgrounds() = runTest(dispatcher) {
        openDemo()
        session.edit(RenameTrack(drumsTrack, "Kick"))
        session.flushSave()
        runCurrent()
        assertEquals(1, saves())
    }

    // --- Startup (T087) ----------------------------------------------------------------------------------------------

    @Test
    fun theFirstLaunchInstallsAndOpensTheDemo() = runTest(dispatcher) {
        val fresh = FakeProjectRepository()
        val session = newSession(fresh)
        AppStartup(session, fresh, scope).start()
        advanceUntilIdle()
        assertEquals(listOf("installDemo", "restoreDemo"), fresh.calls.take(2))
        val demoId = fresh.saved.keys.single()
        assertEquals(demoId, session.state.value.project?.id)
        assertEquals(demoId, fresh.library().first().openProjectId)
    }

    @Test
    fun aLaterLaunchOpensTheLastOpenedProject() = runTest(dispatcher) {
        val other = repository.create("Other")
        repository.setOpen(other.id)
        repository.calls.clear()
        AppStartup(session, repository, scope).start()
        advanceUntilIdle()
        assertEquals(other, session.state.value.project)
        assertTrue(repository.calls.none { it == "installDemo" })
    }

    @Test
    fun filesOpenedAtLaunchMakeTheProjectThatStaysOpenAfterTheStartup() = runTest(dispatcher) {
        // Feature 011 (spec FR-026a): an "open with" request can arrive before the startup reopens the last project; the
        // new project waits for it, so the startup's own open never replaces it (seen on the desktop emulator).
        val other = repository.create("Other")
        repository.setOpen(other.id)
        val startup = AppStartup(session, repository, scope)
        val projects = SessionProjectFromAudio(repository, session, beforeStart = startup::awaitStarted)
        var started = false
        scope.launch { started = projects.start(emptyList(), emptyList()) }
        runCurrent()
        startup.start()
        advanceUntilIdle()
        assertTrue(started)
        assertEquals("Project 1", session.state.value.project?.name)
    }

    private fun saves(): Int = repository.calls.count { it.startsWith("save(") }

    private fun TestScope.collectMessages(): List<SessionMessage> {
        val received = mutableListOf<SessionMessage>()
        backgroundScope.launch { session.messages.collect { received += it } }
        return received
    }
}
