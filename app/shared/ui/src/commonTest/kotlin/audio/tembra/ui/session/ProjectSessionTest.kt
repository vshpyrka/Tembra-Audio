package audio.tembra.ui.session

import audio.tembra.core.metronome.Tempo
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
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.PlayResult
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import kotlinx.coroutines.CoroutineScope
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

    private fun saves(): Int = repository.calls.count { it.startsWith("save(") }

    private fun TestScope.collectMessages(): List<SessionMessage> {
        val received = mutableListOf<SessionMessage>()
        backgroundScope.launch { session.messages.collect { received += it } }
        return received
    }
}
