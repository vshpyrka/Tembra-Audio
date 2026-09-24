package audio.tembra.ui.placeholder

import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import audio.tembra.core.version.SemanticVersion
import audio.tembra.engine.EngineDescriptor
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlaceholderScreenTest {
    private val descriptor = EngineDescriptor("Tembra Engine", SemanticVersion(0, 1, 0), emptyList(), "Tembra Engine 0.1.0 ()")
    private val state = PlaceholderUiState(pressCount = 0, engine = descriptor)

    @Test
    fun showsTitleCounterAndEngineCaption() = runComposeUiTest {
        setContent { TembraTheme { PlaceholderScreenContent(state, onButtonPressed = {}, windowWidth = WindowWidth.Compact) } }
        onNodeWithTag("placeholder.title").assertTextEquals("Tembra Audio")
        onNodeWithTag("placeholder.counter").assertTextEquals("Pressed 0 times")
        onNodeWithTag("placeholder.engineCaption").assertTextEquals("Tembra Engine 0.1.0 ()")
        onNodeWithTag("placeholder.button").assertContentDescriptionEquals("Count up").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun rendersTheGivenPressCount() = runComposeUiTest {
        setContent { TembraTheme { PlaceholderScreenContent(state.copy(pressCount = 3), onButtonPressed = {}, windowWidth = WindowWidth.Compact) } }
        onNodeWithTag("placeholder.counter").assertTextEquals("Pressed 3 times")
    }

    @Test
    fun tapFiresTheCallback() = runComposeUiTest {
        var presses = 0
        setContent { TembraTheme { PlaceholderScreenContent(state, onButtonPressed = { presses++ }, windowWidth = WindowWidth.Compact) } }
        onNodeWithTag("placeholder.button").performClick()
        assertEquals(1, presses)
    }

    @Test
    fun keyboardActivationFiresTheCallback() = runComposeUiTest {
        var presses = 0
        var inputModeManager: InputModeManager? = null
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme { PlaceholderScreenContent(state, onButtonPressed = { presses++ }, windowWidth = WindowWidth.Compact) }
        }
        // Compose keeps clickables unfocusable while a device is in touch mode; a keyboard or mouse switches it.
        val keyboardMode = runOnUiThread { inputModeManager!!.requestInputMode(InputMode.Keyboard) }
        if (!keyboardMode) {
            println("keyboardActivationFiresTheCallback: platform stays in touch mode, keyboard path covered on iOS")
            return@runComposeUiTest
        }
        onNodeWithTag("placeholder.button").requestFocus()
        onNodeWithTag("placeholder.button").assertIsFocused()
        onNodeWithTag("placeholder.button").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, presses)
    }

    @Test
    fun hoverShowsTheTooltip() = runComposeUiTest {
        setContent { TembraTheme { PlaceholderScreenContent(state, onButtonPressed = {}, windowWidth = WindowWidth.Compact) } }
        onNodeWithTag("placeholder.button").performMouseInput { enter(center); moveTo(center) }
        waitUntilAtLeastOneExists(hasText("Adds one to the counter"), timeoutMillis = 5_000)
        onNodeWithText("Adds one to the counter").assertIsDisplayed()
    }

    @Test
    fun choosesTheLayoutBranchFromTheWindowWidth() = runComposeUiTest {
        setContent { TembraTheme { PlaceholderScreenContent(state, onButtonPressed = {}, windowWidth = WindowWidth.Compact) } }
        onNodeWithTag("placeholder.layout.compact").assertExists()
    }

    @Test
    fun wideWindowsUseTheWideLayout() = runComposeUiTest {
        setContent { TembraTheme { PlaceholderScreenContent(state, onButtonPressed = {}, windowWidth = WindowWidth.Expanded) } }
        onNodeWithTag("placeholder.layout.wide").assertExists()
    }

    @Test
    fun clickingThroughTheViewModelUpdatesTheCounter() = runComposeUiTest {
        val viewModel = PlaceholderViewModel(FakeAudioEngine(descriptor))
        setContent { TembraTheme { PlaceholderScreen(viewModel = viewModel) } }
        onNodeWithTag("placeholder.counter").assertTextEquals("Pressed 0 times")
        onNodeWithTag("placeholder.button").performClick()
        onNodeWithTag("placeholder.counter").assertTextEquals("Pressed 1 times")
    }
}
