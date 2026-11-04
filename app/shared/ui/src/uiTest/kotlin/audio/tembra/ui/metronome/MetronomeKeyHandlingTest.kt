package audio.tembra.ui.metronome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Metronome's keys (feature 011, spec FR-018): Space starts and stops, ↑/↓ change the tempo by 1 BPM, with Shift
 * by 10, T taps; keys a focused control uses itself (a slider's arrows) stay with that control.
 */
@OptIn(ExperimentalTestApi::class)
class MetronomeKeyHandlingTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private val callbacks = MetronomeCallbacks.None.copy(
        onTogglePlayback = { calls += "toggle" }, onStep = { calls += "step($it)" }, onTap = { calls += "tap" },
    )

    private fun ComposeUiTest.setRoot() {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.size(200.dp).testTag("root").focusable().metronomeKeyHandling(callbacks))
            }
        }
        enterKeyboardMode(manager)
        onNodeWithTag("root").requestFocus()
    }

    @Test
    fun spaceArrowsAndT() = runComposeUiTest {
        setRoot()
        onNodeWithTag("root").performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            keyUp(Key.ShiftLeft)
            pressKey(Key.T)
        }
        waitForIdle()
        assertEquals(listOf("toggle", "step(1)", "step(-1)", "step(10)", "step(-10)", "tap"), calls)
    }

    @Test
    fun heldArrowsRepeat() = runComposeUiTest {
        setRoot()
        onNodeWithTag("root").performKeyInput {
            keyDown(Key.DirectionUp)
            advanceEventTime(1_000)
            keyUp(Key.DirectionUp)
        }
        assertTrue(calls.count { it == "step(1)" } > 1, "holding ↑ stepped only ${calls.count { it == "step(1)" }} time(s)")
    }
}
