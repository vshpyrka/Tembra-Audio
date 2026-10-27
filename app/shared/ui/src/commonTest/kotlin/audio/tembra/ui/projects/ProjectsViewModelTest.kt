package audio.tembra.ui.projects

import audio.tembra.ui.session.RecordingController
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.FakeAudioRouteMonitor
import audio.tembra.data.timeline.FakeTimelinePreferences
import audio.tembra.core.project.LibraryFilter
import audio.tembra.core.project.LibrarySort
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.Project
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.session.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The library's ViewModel over the fakes (spec FR-050 to FR-054; T116). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var nowMs = T0
    private val repository = FakeProjectRepository(now = { nowMs })
    private val holder = LibraryViewStateHolder()
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs + 5 * MINUTE)
    }
    private val engine = FakeAudioEngine()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = FakeMetronomePreferences(),
        host = FakeBackgroundPlaybackHost(),
        recording = RecordingController(
            audio = engine,
            permission = FakeMicrophonePermission(MicrophonePermissionStatus.Granted),
            routes = FakeAudioRouteMonitor(),
            host = FakeBackgroundPlaybackHost(),
            preferences = FakeTimelinePreferences(),
            repository = repository,
            scope = scope,
            clock = { 0L },
            tokens = { "take-1" },
            mirrorIntervalMs = 100,
        ),
        scope = scope,
        overviewReader = { WaveformOverview(1, 512, 2, emptyList()) },
        ioDispatcher = dispatcher,
    )
    private lateinit var viewModel: ProjectsViewModel
    private lateinit var demo: Project
    private lateinit var alpha: Project
    private lateinit var beta: Project

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** The demo and "Alpha" ten days ago, "Beta" now; the clock reads five minutes past "Beta". */
    private fun TestScope.library() {
        scope.launch {
            demo = repository.restoreDemo()
            alpha = repository.create("Alpha")
            nowMs = T0 + 10 * DAY
            beta = repository.create("Beta")
        }
        runCurrent()
        session.start() // as AppStartup does: the autosave that refreshes the index after a session edit
        viewModel = ProjectsViewModel(repository, session, holder, clock)
        runCurrent()
    }

    private fun TestScope.ui(): ProjectsUiState {
        runCurrent()
        return viewModel.uiState.value
    }

    private fun names() = viewModel.uiState.value.cards.map { it.name }

    @Test
    fun theCardsComeFromTheIndexWithTheirTexts() = runTest(dispatcher) {
        library()
        val state = ui()
        assertTrue(state.loaded)
        assertTrue(state.isFirstContentReady)
        assertEquals(3, state.total)
        assertEquals("3 projects", state.countText)
        assertEquals(listOf("Beta", "Demo project", "Alpha"), names(), "last edited first, then the index order")
        val demoCard = state.cards.first { it.isDemo }
        assertEquals("124 BPM", demoCard.tempoText)
        assertEquals("Dmin", demoCard.keyText)
        assertEquals("00:30.96", demoCard.lengthText)
        assertEquals("4 tracks", demoCard.tracksText)
        assertEquals("17 MB", demoCard.sizeText)
        assertTrue(demoCard.relativeTime.endsWith("2026"), "ten days ago reads as a date: ${demoCard.relativeTime}")
        assertEquals("5 m ago", state.cards.first { it.name == "Beta" }.relativeTime)
        assertEquals("—", state.cards.first { it.name == "Alpha" }.keyText)
        assertFalse(state.cards.any { it.isActive }, "nothing is open yet")
    }

    @Test
    fun searchFilterAndSortCombineAndTheCountReadsMofN() = runTest(dispatcher) {
        library()
        viewModel.setQuery("alp")
        ui()
        assertEquals(listOf("Alpha"), names())
        assertEquals("1 of 3 projects", ui().countText)
        viewModel.setQuery("")
        viewModel.setFilter(LibraryFilter.Recent)
        ui()
        assertEquals(listOf("Beta"), names(), "edited within seven days")
        assertEquals("1 of 3 projects", ui().countText)
        viewModel.setFilter(LibraryFilter.All)
        viewModel.setSort(LibrarySort.Name)
        ui()
        assertEquals(listOf("Alpha", "Beta", "Demo project"), names())
        assertEquals("3 projects", ui().countText)
        viewModel.setQuery("zzz")
        val none = ui()
        assertTrue(none.noMatch)
        assertEquals("0 of 3 projects", none.countText)
    }

    @Test
    fun theQueryOutlivesTheViewModel() = runTest(dispatcher) {
        library()
        viewModel.setQuery("be")
        viewModel.setSort(LibrarySort.Name)
        runCurrent()
        val recreated = ProjectsViewModel(repository, session, holder, clock)
        runCurrent()
        assertEquals("be", recreated.uiState.value.query.text)
        assertEquals(LibrarySort.Name, recreated.uiState.value.query.sort)
        assertEquals(listOf("Beta"), recreated.uiState.value.cards.map { it.name })
    }

    @Test
    fun openingACardOpensTheSessionAndAsksForTheTimeline() = runTest(dispatcher) {
        library()
        val events = mutableListOf<ProjectsEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.open(alpha.id)
        runCurrent()
        assertEquals(alpha.id, session.state.value.project?.id)
        assertEquals(listOf<ProjectsEvent>(ProjectsEvent.OpenedInTimeline), events)
        assertTrue(ui().cards.first { it.name == "Alpha" }.isActive)
    }

    @Test
    fun newProjectCreatesTheNextNumberAndOpensIt() = runTest(dispatcher) {
        library()
        viewModel.newProject()
        runCurrent()
        val state = ui()
        assertEquals(4, state.total)
        val created = state.cards.first { it.name == "Project 1" }
        assertTrue(created.isActive)
        assertEquals(created.id, session.state.value.project?.id)
        assertEquals("120 BPM", created.tempoText)
    }

    @Test
    fun renameValidatesThenSavesThroughTheRepositoryForAClosedProject() = runTest(dispatcher) {
        library()
        viewModel.rename(alpha.id)
        assertIs<ProjectsDialog.Rename>(ui().dialog)
        viewModel.confirmRename("   ")
        val empty = assertIs<ProjectsDialog.Rename>(ui().dialog, "an empty name keeps the dialog")
        assertEquals(ProjectsCopy.nameEmpty, empty.error)
        viewModel.confirmRename("x".repeat(61))
        assertEquals(ProjectsCopy.nameTooLong, assertIs<ProjectsDialog.Rename>(ui().dialog).error)
        viewModel.confirmRename("Fresh")
        assertNull(ui().dialog)
        assertEquals("Fresh", repository.saved.getValue(alpha.id).name)
        assertTrue(ui().cards.any { it.name == "Fresh" })
    }

    @Test
    fun editsOnTheOpenProjectGoThroughTheSessionAndUndo() = runTest(dispatcher) {
        library()
        viewModel.open(alpha.id)
        runCurrent()
        viewModel.rename(alpha.id)
        viewModel.confirmRename("Fresh")
        runCurrent()
        assertEquals("Fresh", session.state.value.project?.name)
        assertTrue(session.state.value.canUndo)
        viewModel.describe(alpha.id)
        viewModel.confirmDescription("x".repeat(121))
        assertEquals(ProjectsCopy.descriptionTooLong, assertIs<ProjectsDialog.Describe>(ui().dialog).error)
        viewModel.confirmDescription("A sketch")
        runCurrent()
        assertEquals("A sketch", session.state.value.project?.description)
        viewModel.chooseKey(alpha.id)
        assertIs<ProjectsDialog.Key>(ui().dialog)
        viewModel.confirmKey(MusicalKey.AMinor)
        runCurrent()
        assertEquals(MusicalKey.AMinor, session.state.value.project?.key)
        advanceTimeBy(600) // the card reads the saved index; the session autosaves half a second after the edit
        assertEquals("Amin", ui().cards.first { it.id == alpha.id }.keyText)
        session.undo()
        assertNull(session.state.value.project?.key)
    }

    @Test
    fun duplicateMakesANumberedCopy() = runTest(dispatcher) {
        library()
        viewModel.duplicate(alpha.id)
        viewModel.duplicate(alpha.id)
        val state = ui()
        assertEquals(5, state.total)
        assertTrue(state.cards.any { it.name == "Alpha copy" })
        assertTrue(state.cards.any { it.name == "Alpha copy 2" })
    }

    @Test
    fun deleteConfirmsAndDeletingTheOpenProjectOpensTheMostRecentRemainingOne() = runTest(dispatcher) {
        library()
        viewModel.delete(demo.id)
        assertIs<ProjectsDialog.Delete>(ui().dialog)
        viewModel.dismissDialog()
        assertNull(ui().dialog)
        assertEquals(3, ui().total)
        viewModel.open(alpha.id)
        runCurrent()
        viewModel.delete(alpha.id)
        viewModel.confirmDelete()
        val state = ui()
        assertEquals(2, state.total)
        assertEquals(beta.id, session.state.value.project?.id, "the most recently edited remaining project opens")
        assertTrue(state.cards.first { it.id == beta.id }.isActive)
    }

    @Test
    fun deletingTheLastProjectCreatesAnEmptyOne() = runTest(dispatcher) {
        library()
        scope.launch {
            repository.delete(demo.id)
            repository.delete(alpha.id)
        }
        runCurrent()
        viewModel.open(beta.id)
        runCurrent()
        viewModel.delete(beta.id)
        viewModel.confirmDelete()
        val state = ui()
        assertEquals(1, state.total)
        assertEquals("Project 1", state.cards.single().name)
        assertEquals(state.cards.single().id, session.state.value.project?.id)
    }

    @Test
    fun restoreDemoReplacesItAndReopensItWhenItWasOpen() = runTest(dispatcher) {
        library()
        viewModel.open(demo.id)
        runCurrent()
        viewModel.restoreDemo()
        runCurrent()
        val state = ui()
        assertEquals(3, state.total)
        val fresh = state.cards.single { it.isDemo }
        assertNotEquals(demo.id, fresh.id)
        assertEquals(fresh.id, session.state.value.project?.id, "the fresh demo took the open one's place")
        assertTrue(fresh.isActive)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
        /** 2026-10-09T00:00:00Z */
        const val T0 = 1_791_504_000_000L
    }
}
