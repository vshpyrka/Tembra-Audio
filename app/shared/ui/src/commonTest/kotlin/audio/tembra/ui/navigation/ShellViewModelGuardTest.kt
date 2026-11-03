package audio.tembra.ui.navigation

import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.DialogRequest
import audio.tembra.ui.session.FakePlaybackGuard
import audio.tembra.ui.session.FakeSessionActivity
import audio.tembra.ui.tracksettings.TrackSettingsKey
import audio.tembra.ui.drop.FakeProjectFromAudio
import audio.tembra.ui.drop.NoOpenedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** The stop-and-leave guard around the shell's navigation (spec FR-008a, contracts/project-session.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelGuardTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var guard: FakePlaybackGuard
    private lateinit var viewModel: ShellViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        guard = FakePlaybackGuard(isPlaying = true)
        viewModel = ShellViewModel(guard, FakeSessionActivity(), NoOpenedFiles, FakeProjectFromAudio())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectingAnotherDestinationWhilePlayingAsksAndNavigatesOnStopAndLeave() = runTest(dispatcher) {
        viewModel.select(Destination.Tuner)
        runCurrent()
        assertEquals(1, guard.asked)
        assertEquals(Destination.Timeline, viewModel.uiState.value.selected)
        assertIs<DialogRequest.StopAndLeave>(viewModel.dialog.value)
        guard.answerLeave(true)
        runCurrent()
        assertEquals(Destination.Tuner, viewModel.uiState.value.selected)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun keepPlayingStaysOnTheTimeline() = runTest(dispatcher) {
        viewModel.select(Destination.Tuner)
        runCurrent()
        guard.answerLeave(false)
        runCurrent()
        assertEquals(Destination.Timeline, viewModel.uiState.value.selected)
        assertTrue(guard.isPlaying)
    }

    @Test
    fun noPromptWhenStopped() = runTest(dispatcher) {
        guard.isPlaying = false
        viewModel.select(Destination.Tuner)
        runCurrent()
        assertEquals(0, guard.asked)
        assertEquals(Destination.Tuner, viewModel.uiState.value.selected)
    }

    @Test
    fun noPromptBetweenTheTimelineAndItsTrackSettings() = runTest(dispatcher) {
        val track = TrackId("t1")
        viewModel.openTrackSettings(track)
        assertEquals(TrackSettingsKey(track), viewModel.uiState.value.trackSettings)
        assertTrue(viewModel.back())
        runCurrent()
        assertEquals(0, guard.asked)
        assertNull(viewModel.uiState.value.trackSettings)
        assertEquals(Destination.Timeline, viewModel.uiState.value.selected)
    }

    @Test
    fun backOnTheTimelineIsNotConsumedAndNeverPrompts() = runTest(dispatcher) {
        assertFalse(viewModel.back())
        runCurrent()
        assertEquals(0, guard.asked)
        assertEquals(Destination.Timeline, viewModel.uiState.value.selected)
    }

    @Test
    fun backToTheTimelineNeverPrompts() = runTest(dispatcher) {
        guard.isPlaying = false
        viewModel.select(Destination.Tuner)
        guard.isPlaying = true
        assertTrue(viewModel.back())
        runCurrent()
        assertEquals(0, guard.asked)
        assertEquals(Destination.Timeline, viewModel.uiState.value.selected)
    }

    @Test
    fun aSecondRequestWhileOneIsPendingIsIgnored() = runTest(dispatcher) {
        viewModel.select(Destination.Tuner)
        runCurrent()
        viewModel.select(Destination.Metronome)
        runCurrent()
        assertEquals(1, guard.asked)
        guard.answerLeave(true)
        runCurrent()
        assertEquals(Destination.Tuner, viewModel.uiState.value.selected)
    }

    @Test
    fun theBackStackIsAListOfNavKeys() = runTest(dispatcher) {
        viewModel.openTrackSettings(TrackId("t2"))
        val stack = viewModel.uiState.value.backStack
        assertEquals(2, stack.size, "the Timeline is the start destination, so its settings sit right above it")
        assertIs<DestinationKey>(stack[0])
        assertIs<TrackSettingsKey>(stack[1])
        assertFalse(viewModel.uiState.value.backStack.isEmpty())
    }
}
