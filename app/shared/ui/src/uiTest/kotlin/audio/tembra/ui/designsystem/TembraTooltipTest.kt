package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The promoted tooltip: shown on hover and on keyboard focus of the injected source, hidden otherwise. */
@OptIn(ExperimentalTestApi::class)
class TembraTooltipTest {

    private fun ComposeUiTest.setTooltip(source: MutableInteractionSource) {
        setContent {
            TembraTheme {
                TembraTooltip(text = "Split", interactionSource = source, testTag = "tip") {
                    Box(Modifier.size(44.dp).testTag("control"))
                }
            }
        }
    }

    @Test
    fun hiddenAtRestShownOnHover() = runComposeUiTest {
        val source = MutableInteractionSource()
        setTooltip(source)
        onNodeWithTag("control").assertExists()
        onNodeWithText("Split").assertDoesNotExist()
        assertTrue(source.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        onNodeWithTag("tip").assertExists()
        onNodeWithText("Split").assertExists()
    }

    @Test
    fun shownOnKeyboardFocusAndHiddenAgainWhenItLeaves() = runComposeUiTest {
        val source = MutableInteractionSource()
        setTooltip(source)
        val focus = FocusInteraction.Focus()
        assertTrue(source.tryEmit(focus))
        waitForIdle()
        onNodeWithText("Split").assertExists()
        assertTrue(source.tryEmit(FocusInteraction.Unfocus(focus)))
        waitForIdle()
        onNodeWithText("Split").assertDoesNotExist()
    }
}
