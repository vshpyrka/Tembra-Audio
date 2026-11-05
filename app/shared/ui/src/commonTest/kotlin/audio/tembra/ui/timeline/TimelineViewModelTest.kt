package audio.tembra.ui.timeline

import audio.tembra.ui.session.RecordingController
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.FakeAudioRouteMonitor
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.CountIn
import audio.tembra.core.recording.MonitorState
import audio.tembra.core.recording.RecordingState
import audio.tembra.ui.transport.RecordButtonState
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.RenameTrack
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.ImportRefusal
import audio.tembra.data.projects.ImportResult
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.PickedFile
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.data.timeline.FakeTimelinePreferences
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.PlayResult
import audio.tembra.ui.drop.DropOutcome
import audio.tembra.ui.drop.DropPlan
import audio.tembra.ui.drop.SkipReason
import audio.tembra.ui.drop.SkippedItem
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.mixer.MixerAnchors
import audio.tembra.ui.mixer.MixerHeight
import audio.tembra.ui.mixer.StripForm
import audio.tembra.ui.platform.FakeAudioFilePicker
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeWindowControls
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.PrimaryModifier
import audio.tembra.ui.shortcuts.ShortcutAction
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutPlatform
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets
import androidx.compose.ui.input.key.Key
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import okio.Buffer

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
    private val preferences = FakeMetronomePreferences()
    private val timelinePreferences = FakeTimelinePreferences()
    private val recordingPreferences = FakeTimelinePreferences()
    private val routes = FakeAudioRouteMonitor()
    private val windowControls = FakeWindowControls()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = preferences,
        host = FakeBackgroundPlaybackHost(),
        recording = RecordingController(
            audio = engine,
            permission = FakeMicrophonePermission(MicrophonePermissionStatus.Granted),
            routes = routes,
            host = FakeBackgroundPlaybackHost(),
            preferences = recordingPreferences,
            repository = repository,
            scope = scope,
            clock = { 0L },
            tokens = { "take-1" },
            mirrorIntervalMs = 100,
        ),
        scope = scope,
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )
    private lateinit var viewModel: TimelineViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TimelineViewModel(session, picker, preferences, timelinePreferences, windowControls)
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

    /** Recording tests also cancel the session scope at the end: the controller's 10 Hz mirror never idles while a track is armed. */
    private fun recordingTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.screenVisible(false)
            scope.cancel()
        }
    }

    /**
     * One mirror tick: the controller's 10 Hz mirror never idles while a track is armed, so the recording tests step
     * the clock instead of advancing until idle.
     */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
    }

    /** Arms the drums and presses Record; with [countIn] off the take runs at once, otherwise the count-in is ended by hand. */
    private fun TestScope.startTake(countIn: CountIn = CountIn.ONE_BAR) {
        viewModel.setCountIn(countIn)
        viewModel.arm(drumsTrack)
        settle()
        assertIs<RecordingState.Armed>(session.state.value.recording.phase)
        viewModel.record()
        settle()
        if (countIn != CountIn.OFF) {
            assertIs<RecordingState.PreRoll>(session.state.value.recording.phase)
            engine.endPreRoll()
            advanceTimeBy(101)
            runCurrent()
        }
        assertIs<RecordingState.Recording>(session.state.value.recording.phase)
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
    fun theLeftEdgeBeatAndTheZoomSurviveEveryWindowWidth() = test {
        // Feature 011 (spec FR-007, SC-003): bar 9 at the left edge and a closer zoom stay put as the window steps
        // 1200 -> 700 -> 400 -> 1200 dp wide; each width re-derives the pixels from the beats.
        openDemo()
        viewModel.viewportMeasured(widthPx = 1200.0 * 2, densityScale = 2.0)
        viewModel.zoomStep(2)
        viewModel.scrollTo(Beats(32.0))
        runCurrent()
        val left = viewModel.uiState.value.viewport.scrollBeats
        val zoom = viewModel.uiState.value.viewport.pixelsPerBeat
        assertEquals(Beats(32.0), left)
        for (widthDp in listOf(700.0, 400.0, 1200.0)) {
            viewModel.viewportMeasured(widthPx = widthDp * 2, densityScale = 2.0)
            runCurrent()
            assertEquals(left, viewModel.uiState.value.viewport.scrollBeats, "left-edge beat at $widthDp dp")
            assertEquals(zoom, viewModel.uiState.value.viewport.pixelsPerBeat, "zoom at $widthDp dp")
        }
    }

    @Test
    fun theFullScreenItemFollowsTheWindowControls() = test {
        // Feature 011 (spec FR-010a, research R21).
        openDemo()
        assertEquals(FullScreenItem.Hidden, viewModel.uiState.value.fullScreenItem)
        windowControls.fullScreenAvailable.value = true
        runCurrent()
        assertEquals(FullScreenItem.Enter, viewModel.uiState.value.fullScreenItem)
        viewModel.setFullScreen(true)
        windowControls.isFullScreen.value = true
        runCurrent()
        assertEquals(FullScreenItem.Exit, viewModel.uiState.value.fullScreenItem)
        viewModel.setFullScreen(false)
        assertEquals(listOf(true, false), windowControls.requests)
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

    /** Spec FR-011 (D1): the scrubbed playhead lands on the grid while it moves and where it is released; Off follows the finger. */
    @Test
    fun aScrubSnapsToTheGridDuringTheDragAndOnRelease() = test {
        openDemo()
        viewModel.scrubStart()
        viewModel.scrub(Beats(2.1))
        runCurrent()
        assertEquals("seek(2.0)", engine.calls.last(), "sixteenth grid during the drag")
        assertEquals(Beats(2.0), ui().positionBeats)
        viewModel.scrubEnd(Beats(3.4))
        runCurrent()
        assertEquals("seek(3.5)", engine.calls.last(), "sixteenth grid on release")
        assertEquals(Beats(3.5), ui().positionBeats)
        assertTrue(ui().following)
        viewModel.setGrid(GridSize.Bar)
        viewModel.scrubStart()
        viewModel.scrubEnd(Beats(5.9))
        runCurrent()
        assertEquals("seek(4.0)", engine.calls.last(), "bar grid on release")
        viewModel.setGrid(GridSize.Off)
        viewModel.scrubStart()
        viewModel.scrub(Beats(6.3))
        viewModel.scrubEnd(Beats(7.3))
        runCurrent()
        assertEquals(listOf("seek(6.3)", "seek(7.3)"), engine.calls.takeLast(2), "Off follows the finger")
        assertEquals(Beats(7.3), ui().positionBeats)
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

    // --- User Story 6 (T112) --------------------------------------------------------------------------------------------

    @Test
    fun expandingATrackOpensItsInlineRowAndAnotherCollapsesTheFirst() = test {
        openDemo()
        viewModel.expandTrack(drumsTrack)
        assertEquals(drumsTrack, ui().expandedTrack)
        viewModel.expandTrack(bassTrack)
        assertEquals(bassTrack, ui().expandedTrack, "one inline row at a time")
        viewModel.expandTrack(null)
        assertNull(ui().expandedTrack)
        viewModel.expandTrack(drumsTrack)
        viewModel.requestDeleteTrack(drumsTrack)
        viewModel.confirmDeleteTrack()
        assertNull(ui().expandedTrack, "a deleted track's row collapses")
    }

    // --- User Story 5 (T107) --------------------------------------------------------------------------------------------

    @Test
    fun aTempoDragIsOneUndoStepAndMovesTheRulerAndTheClick() = test {
        openDemo()
        viewModel.openSheet(TimelineSheet.TempoDialog)
        assertEquals(TimelineSheet.TempoDialog, ui().sheet)
        viewModel.tempoDragging(Tempo(100))
        viewModel.tempoDragging(Tempo(90))
        viewModel.tempoCommit(Tempo(92))
        val state = ui()
        assertEquals(Tempo(92), state.tempo)
        assertTrue(engine.calls.contains("setTempo(92, 4/4)"))
        assertTrue(engine.calls.last { it.startsWith("setGrid") }.startsWith("setGrid(bpm=92"), "the click follows the project tempo")
        viewModel.undo()
        assertEquals(Tempo(124), ui().tempo, "the whole drag undoes as one step")
        assertTrue(state.lengthSeconds > ui().lengthSeconds, "at 92 BPM the same beats last longer than at 124")
    }

    @Test
    fun tapTempoAveragesTheTapsAndEndsAfterTheWindow() = test {
        openDemo()
        viewModel.tapTempo(0L)
        assertEquals(TapCaption.Again, ui().tapCaption)
        viewModel.tapTempo(500_000_000L)
        viewModel.tapTempo(1_000_000_000L)
        val tapped = ui()
        assertEquals(TapCaption.Counting(3), tapped.tapCaption)
        assertEquals(Tempo(120), tapped.tempo, "two half-second intervals")
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(TapCaption.Rest, ui().tapCaption)
        viewModel.undo()
        assertEquals(Tempo(124), ui().tempo, "the run was one undo step")
    }

    @Test
    fun theSignatureIsAnEditAndTheMetronomeSheetWritesTheGlobalPreferences() = test {
        openDemo()
        viewModel.setTimeSignature(TimeSignature.ThreeFour)
        assertEquals(TimeSignature.ThreeFour, ui().timeSignature)
        assertTrue(engine.calls.contains("setTempo(124, 3/4)"))
        val projectBefore = session.state.value.project
        viewModel.openSheet(TimelineSheet.MetronomeSettings)
        viewModel.selectClickSound(ClickSound.BuiltIn(BuiltInClickSound.Cowbell))
        viewModel.selectSubdivision(Subdivision.Two)
        viewModel.selectStyle(MetronomeStyle.Standard)
        viewModel.cycleBeat(1)
        val state = ui()
        val stored = preferences.snapshot.first().settings
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.Cowbell), stored.sound)
        assertEquals(Subdivision.Two, stored.subdivision)
        assertEquals(stored, state.metronomeSettings, "the sheet shows the stored settings")
        assertEquals("selectSound(Cowbell)", engine.calls.last { it.startsWith("selectSound") }, "the session re-applies the grid and the sound")
        assertEquals(3, stored.pattern.levels.size, "the pattern is laid over the project's 3/4")
        assertEquals(projectBefore, session.state.value.project, "sheet changes are never project edits")
        viewModel.openSheet(null)
        assertNull(ui().sheet)
    }

    // --- User Story 4 (T103) --------------------------------------------------------------------------------------------

    @Test
    fun splitGoesThroughTheSessionOnlyWhenThePlayheadLiesInside() = test {
        openDemo()
        viewModel.selectClip(drumsTrack, drumsClip)
        viewModel.split()
        assertEquals(1, ui().tracks[0].clips.size, "no split on the clip's edge")
        viewModel.seek(Beats(8.0))
        viewModel.split()
        val state = ui()
        assertEquals(2, state.tracks[0].clips.size)
        assertEquals(8.0, state.tracks[0].clips[1].startBeats.value, 1e-3)
        assertEquals(Selection.ClipSelected(drumsTrack, state.tracks[0].clips[1].id), state.selection, "the right half is selected")
        assertTrue(state.canUndo)
        viewModel.undo()
        assertEquals(1, ui().tracks[0].clips.size)
        assertTrue(ui().canRedo)
        viewModel.redo()
        assertEquals(2, ui().tracks[0].clips.size)
    }

    /** Spec FR-032 (D1): the scissors cut at the playhead snapped to the grid, and are unavailable when that point sits on an edge. */
    @Test
    fun splitCutsAtTheSnappedPlayhead() = test {
        openDemo()
        viewModel.setGrid(GridSize.Off)
        viewModel.seek(Beats(8.3))
        viewModel.selectClip(drumsTrack, drumsClip)
        assertTrue(ui().canSplit)
        viewModel.setGrid(GridSize.Bar)
        assertTrue(ui().canSplit, "8.3 snaps to the bar line at 8")
        viewModel.split()
        val state = ui()
        assertEquals(2, state.tracks[0].clips.size)
        assertEquals(8.0, state.tracks[0].clips[1].startBeats.value, 1e-3, "the cut lands on the bar line (to a frame), not at 8.3")
        viewModel.undo()
        viewModel.setGrid(GridSize.Off)
        viewModel.seek(Beats(0.3))
        viewModel.selectClip(drumsTrack, drumsClip)
        assertTrue(ui().canSplit, "0.3 lies inside the clip with the grid off")
        viewModel.setGrid(GridSize.Bar)
        assertFalse(ui().canSplit, "0.3 snaps to the clip's start at 0")
        viewModel.split()
        assertEquals(1, ui().tracks[0].clips.size, "no cut on the clip's edge")
    }

    @Test
    fun aDragBuildsOneMoveOnReleaseWithTheSnappedStartAndTheTargetTrack() = test {
        openDemo()
        // The demo's loops fill every track; free the bass lane so the drop has room.
        viewModel.deleteClip(demo.tracks[1].clips[0].id)
        viewModel.clipDragStart(drumsClip)
        val lifted = ui()
        assertEquals(Selection.ClipSelected(drumsTrack, drumsClip), lifted.selection)
        assertEquals(drumsTrack, lifted.drag?.toTrack)
        viewModel.clipDrag(drumsClip, deltaBeats = 4.3, deltaTracks = 1)
        val hovering = ui().drag!!
        assertEquals(bassTrack, hovering.toTrack)
        assertEquals(Beats(4.25), hovering.startBeats, "snapped to the sixteenth grid while hovering")
        assertEquals(1, ui().tracks[0].clips.size, "no edit before release")
        viewModel.clipDragEnd()
        val dropped = ui()
        assertNull(dropped.drag)
        assertEquals(0, dropped.tracks[0].clips.size)
        assertEquals(Beats(4.25), dropped.tracks[1].clips.first { it.id == drumsClip }.startBeats)
        assertEquals(Selection.ClipSelected(bassTrack, drumsClip), dropped.selection)
        assertTrue(dropped.canUndo)
        viewModel.undo()
        assertEquals(1, ui().tracks[0].clips.size, "one undo step restores the clip")
    }

    @Test
    fun aRefusedDropSnapsBackWithoutAnEditAndSaysWhy() = test {
        val received = collectMessages()
        openDemo()
        val keysTrack = demo.tracks[2].id
        viewModel.clipDragStart(drumsClip)
        viewModel.clipDrag(drumsClip, deltaBeats = 0.0, deltaTracks = 2)
        viewModel.clipDragEnd()
        runCurrent()
        val state = ui()
        assertEquals(1, state.tracks[0].clips.size, "the drums clip stays where it was")
        assertEquals(1, state.tracks[2].clips.size, "the keys loop occupies that span")
        assertFalse(state.canUndo)
        assertEquals(listOf(TimelineMessage.EditRejected(audio.tembra.core.project.EditRejection.SpanOccupied)), received)
        assertEquals(TimelineCopy.spanOccupied, received.single().text)
        assertTrue(keysTrack != drumsTrack)
    }

    @Test
    fun aCancelledOrUnmovedDragChangesNothing() = test {
        openDemo()
        viewModel.clipDragStart(drumsClip)
        viewModel.clipDrag(drumsClip, 2.0, 0)
        viewModel.clipDragCancel()
        assertNull(ui().drag)
        assertEquals(Beats.Zero, ui().tracks[0].clips[0].startBeats)
        viewModel.clipDragStart(drumsClip)
        viewModel.clipDragEnd()
        assertFalse(session.state.value.canUndo, "a release without movement is no edit")
    }

    @Test
    fun deleteClipRemovesItAndClearsItsSelection() = test {
        openDemo()
        viewModel.selectClip(drumsTrack, drumsClip)
        viewModel.deleteClip(drumsClip)
        val state = ui()
        assertEquals(0, state.tracks[0].clips.size)
        assertEquals(Selection.None, state.selection)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    // --- User Story 3 (T098) --------------------------------------------------------------------------------------------

    @Test
    fun zoomKeepsTheBeatUnderTheCentreAndClampsToTheBounds() = test {
        openDemo()
        val fit = ui().viewport
        viewModel.zoomAround(centreX = 500.0, factor = 2.0)
        val zoomed = ui().viewport
        assertEquals(fit.pixelsPerBeat * 2, zoomed.pixelsPerBeat, 1e-9)
        assertEquals(fit.beatsAt(500.0).value, zoomed.beatsAt(500.0).value, 1e-9, "the beat under the centre stays put")
        viewModel.zoomAround(500.0, 1e6)
        assertEquals(ui().viewport.closestPixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9, "no closer than one beat per view")
        viewModel.zoomAround(500.0, 1e-6)
        assertEquals(fit.pixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9, "no wider than the fit")
        viewModel.zoomStep(2)
        assertEquals(fit.pixelsPerBeat * 2, ui().viewport.pixelsPerBeat, 1e-9, "two steps double the zoom")
        viewModel.zoomStep(-2)
        assertEquals(fit.pixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9)
    }

    @Test
    fun scrollingSuspendsFollowingAndFitRestoresTheStart() = test {
        openDemo()
        viewModel.zoomStep(4)
        viewModel.scrollBy(120.0)
        val scrolled = ui()
        assertFalse(scrolled.following)
        assertTrue(scrolled.viewport.scrollBeats.value > 0.0)
        viewModel.scrollBy(-10_000.0)
        assertEquals(Beats.Zero, ui().viewport.scrollBeats, "never before the start")
        viewModel.scrollBy(120.0)
        viewModel.fitProject()
        val fitted = ui().viewport
        assertEquals(Beats.Zero, fitted.scrollBeats)
        assertEquals(fitted.fitPixelsPerBeat, fitted.pixelsPerBeat, 1e-9)
    }

    /** The overview strip's entry points (spec FR-015, D1): a span fills the lanes from its start, clamped; a scroll stops following. */
    @Test
    fun theOverviewStripScrollsAndZoomsTheSameViewport() = test {
        openDemo()
        val fit = ui().viewport
        // 1000 px at density 2 are 500 dp: beats 8 to 24 across them is 31.25 dp a beat, beat 8 at the left edge.
        viewModel.zoomTo(Beats(8.0), Beats(24.0))
        val zoomed = ui()
        assertEquals(31.25, zoomed.viewport.pixelsPerBeat, 1e-9)
        assertEquals(Beats(8.0), zoomed.viewport.scrollBeats)
        assertEquals(24.0, zoomed.viewport.endBeats.value, 1e-9)
        assertFalse(zoomed.following, "zooming from the strip is a manual view change")
        viewModel.scrollTo(Beats(12.0))
        assertEquals(Beats(12.0), ui().viewport.scrollBeats)
        assertEquals(31.25, ui().viewport.pixelsPerBeat, 1e-9, "a scroll keeps the zoom")
        viewModel.scrollTo(Beats(-3.0))
        assertEquals(Beats.Zero, ui().viewport.scrollBeats, "never before the start")
        viewModel.zoomTo(Beats(4.0), Beats(4.01))
        assertEquals(ui().viewport.closestPixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9, "no closer than one beat per view")
        assertEquals(Beats(4.0), ui().viewport.scrollBeats, "the span's start stays at the left edge")
        viewModel.zoomTo(Beats.Zero, Beats(10_000.0))
        assertEquals(fit.pixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9, "no wider than the fit")
        viewModel.zoomTo(Beats(16.0), Beats(16.0))
        assertEquals(fit.pixelsPerBeat, ui().viewport.pixelsPerBeat, 1e-9, "an empty span changes nothing")
        viewModel.zoomTo(Beats(8.0), Beats(24.0))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(31.25, repository.viewState(demo.id).zoom?.pixelsPerBeat, "the strip's zoom is remembered per project")
        viewModel.seek(Beats(4.0))
        assertTrue(ui().following, "a ruler tap follows again")
    }

    @Test
    fun zoomAndGridArePersistedOutsideTheDocumentAndRestoredOnReopen() = test {
        openDemo()
        viewModel.zoomStep(2)
        viewModel.setGrid(GridSize.Bar)
        advanceTimeBy(600)
        runCurrent()
        val stored = repository.viewState(demo.id)
        assertEquals(ui().viewport.pixelsPerBeat, stored.zoom?.pixelsPerBeat)
        assertEquals(GridSize.Bar, stored.grid)
        assertFalse(session.state.value.canUndo, "zoom and grid are not edits")
        assertEquals(demo, repository.saved.getValue(demo.id), "the document is untouched")
        // A fresh ViewModel after a relaunch measures the same lane and lands on the stored zoom.
        val later = TimelineViewModel(session, picker, preferences, timelinePreferences, windowControls)
        later.screenVisible(true)
        later.viewportMeasured(1000.0, 2.0)
        runCurrent()
        assertEquals(stored.zoom?.pixelsPerBeat, later.uiState.value.viewport.pixelsPerBeat)
        assertEquals(GridSize.Bar, later.uiState.value.grid)
        later.screenVisible(false)
    }

    /** Spec FR-015 (T151): the strip shows by default; hiding it is a device preference, outside every project, kept on open. */
    @Test
    fun hidingTheOverviewStripIsADevicePreferenceRestoredOnStart() = test {
        openDemo()
        assertTrue(ui().overviewStripShown, "the strip shows by default")
        viewModel.setOverviewStripShown(false)
        assertFalse(ui().overviewStripShown)
        assertFalse(timelinePreferences.stored, "the choice is written to the device preferences")
        advanceTimeBy(600)
        runCurrent()
        assertFalse(session.state.value.canUndo, "showing or hiding the strip is not an edit")
        assertEquals(demo, repository.saved.getValue(demo.id), "the document is untouched")
        // Another project keeps the device's choice: it belongs to no project.
        scope.launch { session.open(repository.create("Second").id) }
        advanceTimeBy(200)
        runCurrent()
        assertEquals("Second", ui().projectName)
        assertFalse(ui().overviewStripShown)
        // A fresh ViewModel after a relaunch reads the stored choice once the timeline shows.
        val later = TimelineViewModel(session, picker, preferences, timelinePreferences, windowControls)
        later.screenVisible(true)
        runCurrent()
        assertFalse(later.uiState.value.overviewStripShown)
        later.setOverviewStripShown(true)
        runCurrent()
        assertTrue(timelinePreferences.stored)
        assertTrue(later.uiState.value.overviewStripShown)
        later.screenVisible(false)
    }

    /** A device that hid the strip before this launch: the stored `false` is what the first shown state carries (T151). */
    @Test
    fun aStoredHiddenStripIsReadWhenTheTimelineShows() = test {
        val hidden = FakeTimelinePreferences(initialShown = false)
        val fresh = TimelineViewModel(session, picker, preferences, hidden, windowControls)
        fresh.screenVisible(true)
        runCurrent()
        assertFalse(fresh.uiState.value.overviewStripShown, "the stored choice hides the strip")
        assertFalse(hidden.stored, "reading writes nothing")
        fresh.screenVisible(false)
    }

    /** T147: the metronome settings are global, so opening another project keeps the sheet's settings and their updates. */
    @Test
    fun theMetronomeSettingsSurviveOpeningAnotherProject() = test {
        openDemo()
        viewModel.selectClickSound(ClickSound.BuiltIn(BuiltInClickSound.Cowbell))
        viewModel.selectSubdivision(Subdivision.Two)
        runCurrent()
        val stored = preferences.snapshot.first().settings
        assertEquals(stored, ui().metronomeSettings, "project A shows the emitted settings")
        scope.launch { session.open(repository.create("Second").id) }
        advanceTimeBy(200)
        runCurrent()
        assertEquals("Second", ui().projectName)
        assertEquals(stored, ui().metronomeSettings, "project B still carries the settings, no defaults until the next emission")
        // The preferences still drive the sheet after the switch.
        preferences.update { it.copy(settings = it.settings.copy(subdivision = Subdivision.Triplet)) }
        assertEquals(Subdivision.Triplet, ui().metronomeSettings.subdivision)
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.Cowbell), ui().metronomeSettings.sound)
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

    // --- Feature 012: the mixer (contracts/mixer-session.md, mixer-ui.md; T046) ---------------------------------------

    @Test
    fun toggleMixerOpensAtHalfAndMeters() = test {
        openDemo()
        viewModel.lanesMeasured(TimelineLayout.Compact, 600.dp)
        assertFalse(engine.metering)
        assertFalse(ui().mixer.open)
        viewModel.toggleMixer()
        assertTrue(engine.metering, "metering starts with the toggle, synchronously")
        assertEquals("setMetering(true)", engine.calls.last())
        val mixer = ui().mixer
        assertTrue(mixer.open)
        assertEquals(MixerHeight.Half, mixer.height)
        assertEquals(StripForm.Compact, mixer.form)
        assertEquals(MixerAnchors(half = 270.dp, full = 600.dp), mixer.anchors)
        assertEquals(listOf("Drums", "Bass", "Keys", "Pad"), mixer.strips.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), mixer.strips.map { it.number })
        assertEquals(listOf(0, 1, 2, 3), mixer.strips.map { it.index })
        assertEquals(80, mixer.master.percent)
        assertEquals(null, mixer.selectedIndex)
        viewModel.selectTrack(bassTrack)
        assertEquals(1, ui().mixer.selectedIndex)
    }

    @Test
    fun toggleAgainClosesAndStopsMetering() = test {
        openDemo()
        viewModel.toggleMixer()
        viewModel.toggleMixer()
        assertFalse(ui().mixer.open)
        assertFalse(engine.metering)
        assertEquals("setMetering(false)", engine.calls.last())
        assertEquals(PlaybackState.Stopped(Beats.Zero), ui().playback, "opening and closing never touches the transport")
    }

    @Test
    fun xTogglesTheMixer() = test {
        openDemo()
        val platform = ShortcutPlatform(PrimaryModifier.Ctrl, menuServesPrimaryChords = false)
        val x = KeyInput(key = Key.X, codePoint = 'x'.code, ctrl = false, meta = false, shift = false, alt = false, down = true)
        val action = ShortcutCatalogue.match(x, ShortcutScope.Timeline, platform)
        assertEquals(ShortcutAction.TimelineMixer, action)
        val callbacks = TimelineCallbacks.None.copy(onMixerToggle = viewModel::toggleMixer)
        val targets = ShortcutTargets(timeline = TimelineKeyTargets({ viewModel.uiState.value }, { callbacks }))
        assertTrue(ShortcutDispatcher.dispatch(ShortcutAction.TimelineMixer, targets))
        assertTrue(ui().mixer.open)
        ShortcutDispatcher.dispatch(ShortcutAction.TimelineMixer, targets)
        assertFalse(ui().mixer.open)
    }

    @Test
    fun stripsFollowTheTracks() = test {
        openDemo()
        viewModel.toggleMixer()
        viewModel.addTrack()
        assertEquals(5, ui().mixer.strips.size)
        assertEquals(5, ui().mixer.strips.last().number)
        assertEquals("Track 1", ui().mixer.strips.last().name)
        session.edit(RenameTrack(drumsTrack, "Kit"))
        assertEquals("Kit", ui().mixer.strips[0].name)
        viewModel.requestDeleteTrack(drumsTrack)
        viewModel.confirmDeleteTrack()
        assertEquals(listOf("Bass", "Keys", "Pad", "Track 1"), ui().mixer.strips.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), ui().mixer.strips.map { it.number }, "numbers are positions, never skipped")
        viewModel.toggleMute(bassTrack)
        viewModel.toggleSolo(bassTrack)
        assertTrue(ui().mixer.strips[0].muted && ui().mixer.strips[0].soloed)
        assertFalse(ui().mixer.strips[1].audible, "solo silences the others")
    }

    @Test
    fun gestureEndClosesTheGroup() = test {
        openDemo()
        viewModel.setGain(drumsTrack, GainDb(-3f))
        viewModel.setGain(drumsTrack, GainDb(-4f))
        viewModel.gestureSettled()
        viewModel.setGain(drumsTrack, GainDb(-5f))
        viewModel.undo()
        assertEquals(GainDb(-4f), ui().tracks[0].gainDb, "the settled run stays as one step")
        viewModel.undo()
        assertEquals(GainDb.Unity, ui().tracks[0].gainDb)
    }

    @Test
    fun aFaderDragIsOneUndoStep() = test {
        openDemo()
        viewModel.toggleMixer()
        for (db in listOf(-1f, -2f, -3f, -4f)) viewModel.setGain(drumsTrack, GainDb(db))
        viewModel.gestureSettled()
        assertEquals(GainDb(-4f), ui().mixer.strips[0].gainDb)
        assertTrue(engine.calls.contains("setTrack(0,0.631,true)"), "the engine followed the drag: ${engine.calls.filter { it.startsWith("setTrack") }}")
        viewModel.undo()
        assertEquals(GainDb.Unity, ui().mixer.strips[0].gainDb)
        assertFalse(ui().canUndo, "the whole drag was one step")
        for (percent in listOf(70, 60, 50)) viewModel.setMasterVolume(percent)
        viewModel.gestureSettled()
        assertEquals(50, ui().mixer.master.percent)
        viewModel.undo()
        assertEquals(80, ui().mixer.master.percent, "the master drag was one step")
    }

    @Test
    fun theLanesBoxDecidesTheAnchorsAndTheFormAndLandscapeForcesFull() = test {
        openDemo()
        viewModel.toggleMixer()
        viewModel.lanesMeasured(TimelineLayout.Compact, 400.dp)
        assertEquals(MixerAnchors(half = 256.dp, full = 400.dp), ui().mixer.anchors)
        viewModel.setMixerHeight(MixerHeight.Full)
        assertEquals(MixerHeight.Full, ui().mixer.height)
        assertEquals(StripForm.FullNoVu, ui().mixer.form, "a 400 dp sheet drops the VU dial")
        viewModel.lanesMeasured(TimelineLayout.Expanded, 800.dp)
        assertEquals(MixerAnchors(half = 608.dp, full = 800.dp), ui().mixer.anchors)
        assertEquals(StripForm.Full, ui().mixer.form)
        viewModel.setMixerHeight(MixerHeight.Half)
        assertEquals(StripForm.Compact, ui().mixer.form)
        viewModel.lanesMeasured(TimelineLayout.Landscape, 300.dp)
        assertEquals(MixerHeight.Full, ui().mixer.height, "landscape has the one anchor")
        assertEquals(StripForm.FullNoVu, ui().mixer.form)
        assertTrue(ui().mixer.open, "the layout change keeps the sheet open")
        viewModel.lanesMeasured(TimelineLayout.Compact, 600.dp)
        assertEquals(MixerHeight.Half, ui().mixer.height, "the asked height returns with the two anchors")
    }

    @Test
    fun theOpenMixerSurvivesAProjectSwitchAndKeepsOneMeteringHolder() = test {
        openDemo()
        viewModel.toggleMixer()
        scope.launch { session.open(repository.create("Second").id) }
        advanceTimeBy(200)
        runCurrent()
        assertEquals("Second", ui().projectName)
        assertTrue(ui().mixer.open, "the sheet stays open across the switch")
        assertEquals(ui().tracks.size, ui().mixer.strips.size)
        assertTrue(engine.metering)
        viewModel.toggleMixer()
        assertFalse(engine.metering, "one holder, released once")
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

    // --- Recording (feature 010, User Story 1; T047) ------------------------------------------------------------------

    @Test
    fun armForwardsToTheSessionAndLightsTheTrackAndArmingAgainDisarms() = recordingTest {
        openDemo()
        viewModel.arm(drumsTrack)
        settle()
        assertTrue(ui().tracks[0].armed)
        assertEquals(drumsTrack, ui().armedTrack)
        assertTrue(engine.armed)
        assertTrue(ui().tracks[0].description.endsWith("armed"))
        viewModel.arm(drumsTrack)
        settle()
        assertFalse(ui().tracks[0].armed)
        assertNull(ui().armedTrack)
        assertFalse(engine.armed)
    }

    @Test
    fun recordWithNothingArmedArmsTheSelectedTrackAndStartsTheCountIn() = recordingTest {
        openDemo()
        viewModel.selectTrack(bassTrack)
        viewModel.record()
        settle()
        val state = ui()
        assertTrue(state.tracks[1].armed)
        assertEquals(RecordButtonState.CountIn, state.recordPhase)
        assertTrue(state.takeRunning)
        assertEquals(4, engine.lastRecordConfig?.countInBeats)
        assertEquals(1, engine.lastRecordConfig?.trackIndex)
    }

    @Test
    fun recordInAnEmptyProjectAddsTrack1SelectsAndArmsIt() = recordingTest {
        openDemo()
        for (track in ui().tracks) {
            viewModel.requestDeleteTrack(track.id)
            viewModel.confirmDeleteTrack()
        }
        assertTrue(ui().isEmpty)
        viewModel.record()
        settle()
        val state = ui()
        assertEquals(1, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].name)
        assertEquals(Selection.TrackSelected(state.tracks[0].id), state.selection)
        assertTrue(state.tracks[0].armed)
        assertTrue(state.takeRunning)
    }

    @Test
    fun recordWhileRecordingEndsTheTakeAndTheClipJoinsTheTrack() = recordingTest {
        openDemo()
        startTake(CountIn.OFF)
        assertEquals(RecordButtonState.Recording, ui().recordPhase)
        assertTrue(ui().isPlaying)
        viewModel.record()
        settle()
        assertTrue("recordStop" in engine.calls)
        assertFalse(engine.takeRunning)
        val state = ui()
        assertFalse(state.takeRunning)
        assertEquals(RecordButtonState.Idle, state.recordPhase)
        assertTrue(state.tracks[0].armed)
        assertTrue(state.tracks[0].clips.any { it.name == "Take 1" })
        assertTrue(state.isPlaying)
    }

    @Test
    fun pauseWhileRecordingEndsTheTakeAndPauses() = recordingTest {
        openDemo()
        startTake(CountIn.OFF)
        viewModel.playPause()
        settle()
        assertTrue("recordStop" in engine.calls)
        assertFalse(engine.takeRunning)
        assertFalse(ui().isPlaying)
        assertFalse(ui().takeRunning)
    }

    @Test
    fun playOrRecordDuringTheCountInCancelsIt() = recordingTest {
        openDemo()
        viewModel.arm(drumsTrack)
        settle()
        viewModel.record()
        settle()
        assertEquals(RecordButtonState.CountIn, ui().recordPhase)
        viewModel.playPause()
        settle()
        assertTrue("recordCancel" in engine.calls)
        assertFalse(ui().takeRunning)
        assertFalse(ui().isPlaying)
        assertTrue(ui().tracks[0].armed)
        assertTrue(ui().tracks[0].clips.none { it.name.startsWith("Take") })
    }

    @Test
    fun theAvailabilityMapFlipsWhileATakeRunsAndTheSeekingIntentsAreRefused() = recordingTest {
        openDemo()
        startTake()
        val recording = ui()
        assertFalse(recording.canAddTrack)
        assertFalse(recording.canImport)
        assertFalse(recording.canEditTempo)
        assertFalse(recording.canEditSignature)
        assertFalse(recording.canEditCountIn)
        assertFalse(recording.canArm)
        assertFalse(recording.canMoveOrDeleteTrack)
        assertFalse(recording.canUndo)
        assertFalse(recording.canRedo)
        assertFalse(recording.transport.seekEnabled)
        val calls = engine.calls.size
        val tracks = recording.tracks.size
        viewModel.skipToStart()
        viewModel.stepBar(1)
        viewModel.seek(Beats(8.0))
        viewModel.scrubStart()
        viewModel.scrub(Beats(8.0))
        viewModel.scrubEnd(Beats(8.0))
        viewModel.addTrack()
        viewModel.arm(bassTrack)
        viewModel.moveTrack(drumsTrack, 1)
        viewModel.requestDeleteTrack(bassTrack)
        viewModel.setTimeSignature(TimeSignature.ThreeFour)
        viewModel.setCountIn(CountIn.OFF)
        runCurrent()
        assertEquals(calls, engine.calls.size)
        val after = ui()
        assertEquals(tracks, after.tracks.size)
        assertTrue(after.tracks[0].armed)
        assertNull(after.confirmDelete)
        assertEquals(TimeSignature.FourFour, after.timeSignature)
        assertEquals(CountIn.ONE_BAR, after.countIn)
        viewModel.record()
        settle()
        val idle = ui()
        assertTrue(idle.canAddTrack)
        assertTrue(idle.canArm)
        assertTrue(idle.transport.seekEnabled)
    }

    @Test
    fun theReadoutLengthGrowsWithTheLiveTake() = recordingTest {
        openDemo()
        val projectLength = ui().lengthSeconds
        startTake(CountIn.OFF)
        engine.nextCapture.framesCaptured = 48_000L * 40
        advanceTimeBy(101)
        runCurrent()
        val state = ui()
        assertEquals(48_000L * 40, state.liveTake?.framesCaptured)
        assertEquals(40.0, state.lengthSeconds, 1e-6)
        assertTrue(state.lengthSeconds > projectLength)
        assertEquals(40.0, state.transport.lengthSeconds, 1e-6)
        assertTrue(state.lengthBeats > Beats(64.0))
    }

    @Test
    fun recordButtonStateFollowsTheBarWidthAndThePhase() = recordingTest {
        openDemo()
        viewModel.arm(drumsTrack)
        settle()
        viewModel.barWidthMeasured(360.dp)
        var state = ui()
        assertEquals(RecordButtonState.Hidden, state.recordButton)
        assertEquals(RecordButtonState.Hidden, state.transport.record)
        assertTrue(state.tracks[0].recordHere)
        assertFalse(state.tracks[1].recordHere)
        viewModel.barWidthMeasured(432.dp)
        state = ui()
        assertEquals(RecordButtonState.Idle, state.recordButton)
        assertFalse(state.tracks[0].recordHere)
        viewModel.record()
        settle()
        engine.nextTransport.countInBeatsLeft = 3
        advanceTimeBy(101)
        runCurrent()
        state = ui()
        assertEquals(RecordButtonState.CountIn, state.recordButton)
        assertEquals(3, state.countInBeatsLeft)
        assertEquals(3, state.transport.countInBeatsLeft)
        engine.endPreRoll()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(RecordButtonState.Recording, ui().recordButton)
        assertEquals(-1, ui().countInBeatsLeft)
    }

    /** The count-in is a project setting, not an undo step (spec FR-015; T062): the history flags do not move. */
    @Test
    fun setCountInIsAProjectSettingOutsideTheHistory() = recordingTest {
        openDemo()
        assertEquals(CountIn.ONE_BAR, ui().countIn)
        val canUndo = ui().canUndo
        viewModel.setCountIn(CountIn.TWO_BARS)
        assertEquals(CountIn.TWO_BARS, ui().countIn)
        assertEquals(canUndo, ui().canUndo)
        assertEquals(CountIn.TWO_BARS, session.state.value.project?.countIn)
        // Two bars of 4/4: the controller receives eight beats.
        viewModel.arm(drumsTrack)
        settle()
        viewModel.record()
        settle()
        assertEquals(8, engine.lastRecordConfig?.countInBeats)
    }

    /** Monitoring (spec FR-016, FR-017; T067): on the speaker the toggle only explains; with headphones it flips the remembered preference and the engine. */
    @Test
    fun toggleMonitorOnTheSpeakerExplainsAndWithHeadphonesFlipsThePreferenceAndTheEngine() = recordingTest {
        openDemo()
        val messages = collectMessages()
        viewModel.arm(drumsTrack)
        settle()
        assertEquals(MonitorState.Off, ui().monitor)
        viewModel.toggleMonitor()
        settle()
        assertEquals(listOf<TimelineMessage>(TimelineMessage.MonitorNeedsHeadphones), messages)
        assertEquals(MonitorState.Off, ui().monitor)
        assertTrue(engine.calls.none { it.startsWith("setMonitor(true") })
        routes.plugHeadphones()
        settle()
        viewModel.toggleMonitor()
        settle()
        assertEquals(MonitorState.On, ui().monitor)
        assertEquals(true, recordingPreferences.storedMonitorInput)
        assertTrue(engine.calls.any { it.startsWith("setMonitor(true") })
        viewModel.toggleMonitor()
        settle()
        assertEquals(MonitorState.Off, ui().monitor)
        assertEquals(false, recordingPreferences.storedMonitorInput)
    }

    @Test
    fun resetClipReachesTheEngine() = recordingTest {
        openDemo()
        viewModel.arm(drumsTrack)
        settle()
        viewModel.resetClip()
        assertEquals("resetClip", engine.calls.last())
    }

    @Test
    fun aDropOfOneFileOnALaneJoinsThatTrackAndNamesWhatItLeftOut() = test {
        // Feature 011 (spec FR-026): the planned target goes to the session; skipped items share the refusal message.
        val received = collectMessages()
        openDemo()
        val before = ui().tracks.first { it.id == drumsTrack }.clips.size
        repository.importResults += accepted("kick.wav", "ee")
        val file = PickedFile("kick.wav") { Buffer() }
        viewModel.drop(DropOutcome(DropPlan.AddToTrack(drumsTrack, Beats(64.0), file), listOf(SkippedItem("notes.txt", SkipReason.NotAudio))))
        runCurrent()
        assertEquals(before + 1, ui().tracks.first { it.id == drumsTrack }.clips.size)
        val message = assertIs<TimelineMessage.ImportRefused>(received.single())
        assertEquals(listOf("notes.txt" to "not an audio file"), message.refusals)
    }

    @Test
    fun aDropOfSeveralFilesMakesNewTracks() = test {
        openDemo()
        repository.importResults += accepted("a.wav", "a1")
        repository.importResults += accepted("b.wav", "b1")
        val files = listOf(PickedFile("a.wav") { Buffer() }, PickedFile("b.wav") { Buffer() })
        viewModel.drop(DropOutcome(DropPlan.NewTracks(files, Beats(8.0)), emptyList()))
        runCurrent()
        assertEquals(6, ui().tracks.size)
    }

    @Test
    fun aDropWithNoAudioSaysSo() = test {
        val received = collectMessages()
        openDemo()
        viewModel.drop(DropOutcome(DropPlan.Nothing, listOf(SkippedItem("notes.txt", SkipReason.NotAudio))))
        runCurrent()
        assertEquals("No audio files were found: notes.txt (not an audio file)", received.single().text)
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
