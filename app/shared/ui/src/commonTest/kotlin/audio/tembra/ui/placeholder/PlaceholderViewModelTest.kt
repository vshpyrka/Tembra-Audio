package audio.tembra.ui.placeholder

import audio.tembra.core.version.SemanticVersion
import audio.tembra.engine.EngineDescriptor
import audio.tembra.engine.FakeAudioEngine
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceholderViewModelTest {
    private val descriptor = EngineDescriptor("Tembra Engine", SemanticVersion(0, 1, 0), listOf("miniaudio 0.11.25"), "Tembra Engine 0.1.0 (miniaudio 0.11.25)")
    private lateinit var engine: FakeAudioEngine
    private lateinit var viewModel: PlaceholderViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        engine = FakeAudioEngine(descriptor)
        viewModel = PlaceholderViewModel(engine)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsAtZeroWithTheEngineDescriptor() = runTest {
        assertEquals(PlaceholderUiState(pressCount = 0, engine = descriptor), viewModel.uiState.value)
    }

    @Test
    fun incrementsByOnePerPress() = runTest {
        repeat(3) { viewModel.onButtonPressed() }
        assertEquals(3, viewModel.uiState.value.pressCount)
    }

    @Test
    fun readsTheEngineDescriptorExactlyOnce() = runTest {
        assertEquals(1, engine.describeCalls)
        viewModel.onButtonPressed()
        assertEquals(1, engine.describeCalls)
    }
}
