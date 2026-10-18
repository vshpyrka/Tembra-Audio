package audio.tembra.ui.timeline

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.PlaybackState
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.ImportRefusal
import audio.tembra.data.projects.ImportResult
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.PlayResult
import audio.tembra.ui.platform.FakeAudioFilePicker
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Timeline ViewModel over the session and the fakes (contracts/timeline-screen.md; T077, User Story 1). */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drumsTrack = demo.tracks[0].id
    private val drumsClip = demo.tracks[0].clips[0].id
    private val bassTrack = demo.tracks[1].id
    private val drumsAsset = DemoProject.BUNDLED_ASSET_IDS.getValue("drums")
    private val overview = WaveformOverview(channels = 1, frames = 512, completedBaseBuckets = 2, levels = emptyList())
    private val engine = FakeAudioEngine()
    private val repository = FakeProjectRepository(initial = listOf(demo))
    private val picker = FakeAudioFilePicker()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = FakeMetronomePreferences(),
        host = FakeBackgroundPlaybackHost(),
        scope = scope,
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )
    private lateinit var viewModel: TimelineViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TimelineViewModel(session, picker)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** Every test hides the screen when it ends: `runTest` drains the scheduler, and the 10 Hz poll is an endless timer. */
    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.screenVisible(false)
        }
    }

    /** The derived state after the scheduler ran: `uiState` is combined on the ViewModel's scope, never inline. */
    private fun TestScope.ui(): TimelineUiState {
        runCurrent()
        return viewModel.uiState.value
    }

    /** Startup opens the last-opened project: the fixture demo with its fixed ids (a fresh library would install a random one). */
    private fun TestScope.openDemo() {
        repository.calls.clear()
        scope.launch { repository.setOpen(demo.id) }
        runCurrent()
        AppStartup(session, repository, scope).start()
        advanceUntilIdle()
        viewModel.screenVisible(true)
        viewModel.viewportMeasured(widthPx = 1000.0, densityScale = 2.0)
        runCurrent()
    }

    private fun TestScope.collectMessages(): List<TimelineMessage> {
        val received = mutableListOf<TimelineMessage>()
        backgroundScope.launch { viewModel.messages.collect { received += it } }
        return received
    }

    @Test
    fun theFirstStateShowsTheDemoProject() = test {
        assertFalse(viewModel.uiState.value.loaded)
        openDemo()
        val state = ui()
        assertTrue(state.loaded)
        assertEquals(DemoProject.NAME, state.projectName)
        assertEquals(Tempo(124), state.tempo)
        assertEquals(listOf("Drums", "Bass", "Keys", "Pad"), state.tracks.map { it.name })
        assertEquals(5, state.tracks.sumOf { it.clips.size })
        assertEquals("00:00.00 / 00:30.96", state.transport.readoutText)
        assertEquals(PlaybackState.Stopped(Beats.Zero), state.playback)
        assertEquals(GridSize.Default, state.grid)
        assertTrue(state.tracks.all { track -> track.clips.all { it.overview === overview && it.available } })
        assertTrue(state.isFirstContentReady, "every overview is ready")
    }

    @Test
    fun firstContentWaitsForEveryVisibleOverview() = test {
        repository.overviewStatuses.update { it + (drumsAsset to OverviewStatus.Building(0.5f)) }
        openDemo()
        assertFalse(ui().isFirstContentReady)
        assertEquals(OverviewStatus.Building(0.5f), ui().tracks[0].clips[0].overviewStatus)
        repository.overviewStatuses.update { it + (drumsAsset to OverviewStatus.Ready) }
        // Never advanceUntilIdle once the screen is visible: the 10 Hz poll is an endless timer under virtual time.
        advanceTimeBy(50)
        runCurrent()
        assertTrue(ui().isFirstContentReady)
    }

    @Test
    fun theViewportFitsTheProjectOnTheFirstMeasure() = test {
        openDemo()
        val port = ui().viewport
        assertEquals(1000.0, port.widthPx)
        assertEquals(1000.0 / (128.0 * 2.0), port.pixelsPerBeat, "fit to 32 bars: the demo's 16 bars are shorter")
        assertEquals(64.0, port.projectLengthBeats.value, 1e-3, "the demo's loops are 64 beats at 124 BPM")
    }

    @Test
    fun playPauseSkipAndStepGoToTheSession() = test {
        openDemo()
        viewModel.playPause()
        runCurrent()
        assertEquals(PlaybackState.Playing, ui().playback)
        assertTrue(ui().isPlaying)
        viewModel.playPause()
        runCurrent()
        assertIs<PlaybackState.Stopped>(ui().playback)
        viewModel.stepBar(1)
        runCurrent()
        assertEquals("seek(4.0)", engine.calls.last())
        assertEquals(Beats(4.0), ui().positionBeats)
        viewModel.skipToStart()
        runCurrent()
        assertEquals("seek(0.0)", engine.calls.last())
        assertEquals(Beats.Zero, ui().positionBeats)
    }

    @Test
    fun aRulerTapSnapsToTheGridAndFollowsAgain() = test {
        openDemo()
        viewModel.scrollBy(10.0)
        assertFalse(ui().following)
        viewModel.seek(Beats(4.3))
        runCurrent()
        assertEquals("seek(4.25)", engine.calls.last(), "sixteenth grid")
        assertTrue(ui().following)
        assertEquals(Beats(4.25), ui().positionBeats)
        viewModel.setGrid(GridSize.Bar)
        viewModel.seek(Beats(5.9))
        runCurrent()
        assertEquals("seek(4.0)", engine.calls.last(), "bar grid")
    }

    @Test
    fun scrubbingPausesSilentlyAndResumesFromTheReleasePoint() = test {
        val received = collectMessages()
        openDemo()
        viewModel.playPause()
        runCurrent()
        viewModel.scrubStart()
        assertEquals("pause", engine.calls.last())
        viewModel.scrub(Beats(2.0))
        viewModel.scrubEnd(Beats(3.0))
        runCurrent()
        assertEquals(listOf("seek(3.0)", "play"), engine.calls.takeLast(2))
        assertEquals(PlaybackState.Playing, ui().playback)
        assertTrue(received.isEmpty())
        viewModel.playPause()
        runCurrent()
        viewModel.scrubStart()
        viewModel.scrubEnd(Beats(1.0))
        runCurrent()
        assertIs<PlaybackState.Stopped>(ui().playback, "a stopped transport stays stopped after a scrub")
    }

    @Test
    fun theMasterVolumeIsAnEditThatReachesTheEngine() = test {
        openDemo()
        viewModel.setMasterVolume(50)
        assertEquals("setMaster(0.5)", engine.calls.last())
        assertEquals(50, ui().masterVolume)
        assertTrue(ui().canUndo)
    }

    @Test
    fun theMetronomeToggleAndTheGridArePersisted() = test {
        openDemo()
        viewModel.toggleMetronome()
        assertTrue(ui().metronomeOn)
        assertEquals("setMetronome(true)", engine.calls.last())
        viewModel.setGrid(GridSize.Eighth)
        assertEquals(GridSize.Eighth, ui().grid)
        advanceTimeBy(600)
        runCurrent()
        assertTrue(repository.calls.any { it == "saveViewState(${demo.id.value})" })
        assertEquals(GridSize.Eighth, repository.viewState(demo.id).grid)
    }

    @Test
    fun selectionAndSplitAvailabilityFollowThePlayhead() = test {
        openDemo()
        viewModel.selectClip(drumsTrack, drumsClip)
        assertEquals(Selection.ClipSelected(drumsTrack, drumsClip), ui().selection)
        assertFalse(ui().canSplit, "the playhead sits on the clip's start")
        viewModel.seek(Beats(8.0))
        runCurrent()
        assertTrue(ui().canSplit)
        viewModel.clearSelection()
        assertFalse(ui().canSplit)
    }

    @Test
    fun theFollowingViewPagesAfterThePlayhead() = test {
        openDemo()
        viewModel.zoomStep(6)
        val port = ui().viewport
        assertTrue(port.visibleBeats < 64.0, "zoomed in enough that the project overflows the view")
        viewModel.playPause()
        runCurrent()
        engine.nextTransport.positionBeats = port.visibleBeats + 1.0
        engine.nextTransport.audiblePositionSeconds = 20.0
        advanceTimeBy(150)
        runCurrent()
        val paged = ui()
        assertEquals(Beats(port.visibleBeats + 1.0), paged.positionBeats)
        assertEquals(20.0, paged.positionSeconds)
        assertEquals(port.visibleBeats + 1.0, paged.viewport.scrollBeats.value, "the view paged to the playhead")
    }

    // --- User Story 2 (T090) --------------------------------------------------------------------------------------------

    @Test
    fun addTrackAppendsAndSelectsTheNewTrack() = test {
        openDemo()
        viewModel.addTrack()
        val state = ui()
        assertEquals(5, state.tracks.size)
        assertEquals("Track 1", state.tracks.last().name)
        assertEquals(Selection.TrackSelected(state.tracks.last().id), state.selection)
        assertEquals("setArrangement(tracks=5,clips=5)", engine.calls.last())
    }

    @Test
    fun headerTogglesAndTheGainAreSingleEdits() = test {
        openDemo()
        viewModel.selectTrack(drumsTrack)
        assertEquals(Selection.TrackSelected(drumsTrack), ui().selection)
        viewModel.toggleMute(drumsTrack)
        assertTrue(ui().tracks[0].muted)
        assertFalse(ui().tracks[0].audible)
        viewModel.toggleSolo(drumsTrack)
        assertTrue(ui().tracks[0].soloed)
        assertFalse(ui().tracks[1].audible, "solo silences the others")
        viewModel.setGain(drumsTrack, GainDb(-3f))
        assertEquals(GainDb(-3f), ui().tracks[0].gainDb)
        viewModel.undo()
        assertEquals(GainDb.Unity, ui().tracks[0].gainDb, "each change is its own undo step")
    }

    @Test
    fun theTrackMenuMovesAndDeletesWithConfirmation() = test {
        openDemo()
        viewModel.moveTrack(drumsTrack, 1)
        assertEquals(drumsTrack, ui().tracks[1].id)
        viewModel.moveTrack(drumsTrack, -1)
        assertEquals(drumsTrack, ui().tracks[0].id)
        viewModel.requestDeleteTrack(drumsTrack)
        assertEquals(drumsTrack, ui().confirmDelete)
        assertEquals(4, ui().tracks.size)
        viewModel.dismissDeleteTrack()
        assertNull(ui().confirmDelete)
        viewModel.requestDeleteTrack(drumsTrack)
        viewModel.confirmDeleteTrack()
        val state = ui()
        assertNull(state.confirmDelete)
        assertEquals(3, state.tracks.size)
        assertTrue(state.tracks.none { it.id == drumsTrack })
    }

    @Test
    fun addAudioOnATrackPicksOneFileAndImportsOntoIt() = test {
        openDemo()
        val id = AudioAssetId("ee".repeat(32))
        val asset = AudioAsset("lead.wav", AudioFormat.Wav, channels = 2, sampleRate = 48_000, frames = 48_000, byteSize = 192_044)
        repository.importResults += ImportResult.Accepted(id, asset, ClipTemplate.forAsset(id, asset), true, "/fake/audio/lead.wav", "/fake/cache/lead.peaks")
        picker.next = FakeAudioFilePicker.picked("lead.wav")
        viewModel.seek(Beats(64.0))
        viewModel.addAudio(drumsTrack)
        val state = ui()
        assertEquals(listOf("pickOne"), picker.calls)
        assertTrue(repository.calls.any { it == "importAudio(${demo.id.value}, lead.wav)" })
        assertEquals(2, state.tracks[0].clips.size)
        assertEquals(64.0, state.tracks[0].clips.last().startBeats.value, 1e-3, "the clip lands at the playhead, after the loop that ends there")
    }

    @Test
    fun addAudioAsNewTrackPicksManyAndAppendsOneTrackPerFile() = test {
        openDemo()
        repository.importResults += accepted("a.wav", "aa")
        repository.importResults += accepted("b.wav", "bb")
        picker.next = FakeAudioFilePicker.picked("a.wav" to ByteArray(0), "b.wav" to ByteArray(0))
        viewModel.addAudioAsNewTrack()
        val state = ui()
        assertEquals(listOf("pickMany"), picker.calls)
        assertEquals(listOf("Drums", "Bass", "Keys", "Pad", "a", "b"), state.tracks.map { it.name })
    }

    @Test
    fun addAudioWithoutASelectionBehavesAsNewTrack() = test {
        openDemo()
        repository.importResults += accepted("c.wav", "cc")
        picker.next = FakeAudioFilePicker.picked("c.wav")
        viewModel.addAudio(null)
        assertEquals(5, ui().tracks.size)
        assertEquals(listOf("pickMany"), picker.calls)
        viewModel.selectTrack(bassTrack)
        repository.importResults += accepted("d.wav", "dd")
        picker.next = FakeAudioFilePicker.picked("d.wav")
        viewModel.addAudio(null)
        assertEquals(2, ui().tracks[1].clips.size, "with a selection the file lands on the selected track")
        assertEquals(listOf("pickMany", "pickOne"), picker.calls)
    }

    @Test
    fun refusedImportsSurfaceAsOneMessage() = test {
        val received = collectMessages()
        openDemo()
        repository.importResults += ImportResult.Refused("x.ogg", ImportRefusal.UnsupportedFormat)
        repository.importResults += ImportResult.Refused("y.wav", ImportRefusal.TooManyChannels)
        picker.next = FakeAudioFilePicker.picked("x.ogg" to ByteArray(0), "y.wav" to ByteArray(0))
        viewModel.addAudioAsNewTrack()
        runCurrent()
        val message = assertIs<TimelineMessage.ImportRefused>(received.single())
        assertEquals(listOf("x.ogg", "y.wav"), message.refusals.map { it.first })
        assertEquals("2 files could not be added: x.ogg (not a WAV, FLAC or MP3 file), y.wav (more than two channels)", message.text)
    }

    private fun accepted(fileName: String, hex: String): ImportResult.Accepted {
        val id = AudioAssetId(hex.repeat(32))
        val asset = AudioAsset(fileName, AudioFormat.Wav, channels = 1, sampleRate = 48_000, frames = 48_000, byteSize = 96_044)
        return ImportResult.Accepted(id, asset, ClipTemplate.forAsset(id, asset), true, "/fake/audio/$fileName", "/fake/cache/$fileName.peaks")
    }

    @Test
    fun aFailedStartIsAMessage() = test {
        val received = collectMessages()
        openDemo()
        engine.failNextPlay = PlayResult.Failed(-1)
        viewModel.playPause()
        runCurrent()
        assertEquals(listOf(TimelineMessage.StartFailed), received)
        assertEquals(TimelineCopy.couldNotStart, received.single().text)
    }

    @Test
    fun leavingTheScreenKeepsPlaybackAndStopsThePoll() = test {
        openDemo()
        viewModel.playPause()
        runCurrent()
        viewModel.screenVisible(false)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(PlaybackState.Playing, ui().playback)
        assertTrue(engine.calls.none { it == "pause" })
    }
}
