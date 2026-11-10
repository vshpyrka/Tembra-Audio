package audio.tembra.ui.mixer

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.ProjectId
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.data.timeline.FakeTimelinePreferences
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeAudioRouteMonitor
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeMicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.session.RecordingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mixer window's ViewModel over the same session the timeline edits (feature 012, spec FR-021a; T101): its strips
 * follow the session's project and an edit made in the window is the document's edit (one undo step per settled drag),
 * metering is held while it lives, and the state ends when the project is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MixerWindowViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drums = demo.tracks[0].id
    private val overview = WaveformOverview(channels = 1, frames = 512, completedBaseBuckets = 2, levels = emptyList())
    private val engine = FakeAudioEngine()
    private val repository = FakeProjectRepository(initial = listOf(demo))
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
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
            scope.cancel()
        }
    }

    private fun TestScope.openDemo(): MixerWindowViewModel {
        scope.launch { repository.setOpen(demo.id) }
        runCurrent()
        AppStartup(session, repository, scope).start()
        advanceUntilIdle()
        val viewModel = MixerWindowViewModel(session)
        runCurrent()
        return viewModel
    }

    @Test
    fun theStripsFollowTheSessionAndAnEditInTheWindowIsTheDocumentsEdit() = test {
        val viewModel = openDemo()
        val state = viewModel.uiState.value
        assertTrue(state.open)
        assertEquals(listOf("Drums", "Bass", "Keys", "Pad"), state.strips.map { it.name })
        assertEquals(80, state.master.percent)
        assertEquals("setMetering(true)", engine.calls.last(), "the window holds a metering count while it lives")
        viewModel.toggleMute(drums)
        runCurrent()
        assertTrue(viewModel.uiState.value.strips[0].muted)
        assertFalse(viewModel.uiState.value.strips[0].audible)
        assertTrue(session.state.value.project!!.tracks[0].muted, "the timeline's document is the one edited")
    }

    @Test
    fun aFaderDragInTheWindowIsOneUndoStep() = test {
        val viewModel = openDemo()
        for (db in listOf(-1f, -2f, -3f)) viewModel.setGain(drums, GainDb(db))
        viewModel.gestureSettled()
        runCurrent()
        assertEquals(GainDb(-3f), viewModel.uiState.value.strips[0].gainDb)
        session.undo()
        runCurrent()
        assertEquals(GainDb.Unity, viewModel.uiState.value.strips[0].gainDb)
        assertFalse(session.state.value.canUndo, "the whole drag was one step")
    }

    @Test
    fun theStateEndsWhenTheProjectIsGone() = test {
        val viewModel = openDemo()
        assertTrue(viewModel.uiState.value.open)
        session.open(ProjectId("missing"))
        runCurrent()
        assertFalse(viewModel.uiState.value.open)
        assertTrue(viewModel.uiState.value.strips.isEmpty())
    }
}
