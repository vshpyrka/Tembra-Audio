package audio.tembra.ui.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test

/** The top bar shows the product name and the title, and nothing else (spec FR-007, FR-019). */
@OptIn(ExperimentalTestApi::class)
class TembraTopBarTest {

    private val textNodes = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    @Test
    fun showsTheProductNameAndTheTitle() = runComposeUiTest {
        setContent { TembraTheme { TembraTopBar(title = "Tuner") } }
        onNodeWithText("TEMBRA AUDIO").assertIsDisplayed()
        onNodeWithTag("chrome.topBar.title").assertTextEquals("Tuner")
        onNodeWithTag("chrome.topBar").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun showsNothingElse() = runComposeUiTest {
        setContent { TembraTheme { TembraTopBar(title = "Tuner") } }
        onAllNodes(hasAnyAncestor(hasTestTag("chrome.topBar")) and textNodes, useUnmergedTree = true).assertCountEquals(2)
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
