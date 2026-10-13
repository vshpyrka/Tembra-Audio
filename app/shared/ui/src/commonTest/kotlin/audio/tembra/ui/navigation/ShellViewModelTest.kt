package audio.tembra.ui.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Navigation state logic on the JVM host and the iOS simulator (spec FR-001, FR-008, FR-010, FR-020). */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    private lateinit var viewModel: ShellViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = ShellViewModel(FakePlaybackGuard())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsOnHome() = runTest {
        assertEquals(Destination.Home, viewModel.uiState.value.selected)
    }

    @Test
    fun selectShowsTheDestination() = runTest {
        viewModel.select(Destination.Tuner)
        assertEquals(Destination.Tuner, viewModel.uiState.value.selected)
    }

    @Test
    fun reselectingTheActiveDestinationEmitsNothing() = runTest {
        viewModel.select(Destination.Tuner)
        val before = viewModel.uiState.value
        viewModel.select(Destination.Tuner)
        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun backFromADestinationReturnsHomeAndIsConsumed() = runTest {
        viewModel.select(Destination.Tuner)
        assertTrue(viewModel.back())
        assertEquals(Destination.Home, viewModel.uiState.value.selected)
    }

    @Test
    fun backOnHomeIsNotConsumedAndChangesNothing() = runTest {
        val before = viewModel.uiState.value
        assertFalse(viewModel.back())
        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun theLastSelectionWins() = runTest {
        viewModel.select(Destination.Tuner)
        viewModel.select(Destination.Profile)
        assertEquals(Destination.Profile, viewModel.uiState.value.selected)
    }
}
