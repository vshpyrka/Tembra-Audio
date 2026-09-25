package audio.tembra.ui.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test

/** The 320 dp side panel frame (spec FR-006, FR-018, FR-019). */
@OptIn(ExperimentalTestApi::class)
class TembraSupportingPaneTest {

    @Test
    fun showsTheTitleAndTheContentAt320dp() = runComposeUiTest {
        setContent { TembraTheme { TembraSupportingPane(title = "Library") { Text("body") } } }
        onNodeWithText("Library").assertIsDisplayed()
        onNodeWithText("body").assertIsDisplayed()
        onNodeWithTag("chrome.supportingPane").assertWidthIsEqualTo(320.dp)
    }
}
