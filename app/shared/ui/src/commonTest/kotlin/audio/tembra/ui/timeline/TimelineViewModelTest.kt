package audio.tembra.ui.timeline

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.PlaybackState
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
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
import kotlin.test.assertTrue

/** The Timeline ViewModel over the session and the fakes (contracts/timeline-screen.md; T077, User Story 1). */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drumsTrack = demo.tracks[0].id
    private val drumsClip = demo.tracks[0].clips[0].id
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
