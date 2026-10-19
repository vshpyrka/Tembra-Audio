package audio.tembra.ui.timeline

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
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.platform.FakeAudioFilePicker
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
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
    private val preferences = FakeMetronomePreferences()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = preferences,
        host = FakeBackgroundPlaybackHost(),
        scope = scope,
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )
    private lateinit var viewModel: TimelineViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TimelineViewModel(session, picker, preferences)
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
        val later = TimelineViewModel(session, picker, preferences)
        later.screenVisible(true)
        later.viewportMeasured(1000.0, 2.0)
        runCurrent()
        assertEquals(stored.zoom?.pixelsPerBeat, later.uiState.value.viewport.pixelsPerBeat)
        assertEquals(GridSize.Bar, later.uiState.value.grid)
        later.screenVisible(false)
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
