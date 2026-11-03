package audio.tembra.ui.timeline

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Escape during a clip drag (feature 011, spec FR-013): the timeline's key handling cancels the drag, so the clip returns
 * to where it was; the pointer's later moves and its release then find no drag (the ViewModel ignores them,
 * `TimelineViewModelTest.aCancelledDragIgnoresTheRestOfTheGesture`). Without a drag, Escape is not the timeline's.
 */
@OptIn(ExperimentalTestApi::class)
class ClipDragEscapeTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private var state = TimelineUiState(loaded = true)
    private val callbacks = TimelineCallbacks.None.copy(onClipDragCancel = { calls += "cancel" })

    private fun drag() = ClipDragUi(ClipId("c1"), TrackId("t1"), TrackId("t1"), Beats(4.0), Beats(4.0), "drums")

    @Test
    fun escapeCancelsADragAndOtherwiseFallsThrough() = runComposeUiTest {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.size(200.dp).testTag("root").focusable().timelineKeyHandling({ state }, callbacks))
            }
        }
        enterKeyboardMode(manager)
        onNodeWithTag("root").requestFocus()
        state = state.copy(drag = drag())
        onNodeWithTag("root").performKeyInput { pressKey(Key.Escape) }
        assertEquals(listOf("cancel"), calls)
        state = state.copy(drag = null)
        onNodeWithTag("root").performKeyInput { pressKey(Key.Escape) }
        assertEquals(listOf("cancel"), calls, "Escape without a drag is left to the shell")
    }
}
