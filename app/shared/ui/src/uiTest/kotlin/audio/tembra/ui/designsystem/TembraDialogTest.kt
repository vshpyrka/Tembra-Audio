package audio.tembra.ui.designsystem

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The confirmation dialog: title, text and both actions reachable by their tags. */
@OptIn(ExperimentalTestApi::class)
class TembraDialogTest {

    private var confirmed = 0
    private var dismissed = 0

    private fun ComposeUiTest.setDialog() {
        setContent {
            TembraTheme {
                TembraDialog(
                    title = "Stop playback?",
                    text = "You are leaving the timeline while it is playing.",
                    confirm = DialogAction("Stop and leave", onClick = { confirmed++ }, testTag = "dialog.confirm"),
                    dismiss = DialogAction("Keep playing", onClick = { dismissed++ }, testTag = "dialog.cancel"),
                    testTag = "dialog",
                )
            }
        }
    }

    @Test
    fun showsTitleTextAndBothActions() = runComposeUiTest {
        setDialog()
        onNodeWithText("Stop playback?").assertExists()
        onNodeWithText("You are leaving the timeline while it is playing.").assertExists()
        onNodeWithTag("dialog.confirm").assertHasClickAction()
        onNodeWithTag("dialog.cancel").assertHasClickAction()
    }

    @Test
    fun eachActionRunsItsOwnCallback() = runComposeUiTest {
        setDialog()
        onNodeWithTag("dialog.confirm").performClick()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
        onNodeWithTag("dialog.cancel").performClick()
        assertEquals(1, dismissed)
    }
}
