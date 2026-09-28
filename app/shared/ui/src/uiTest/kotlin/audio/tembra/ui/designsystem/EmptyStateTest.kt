package audio.tembra.ui.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EmptyStateTest {

    @Test
    fun showsTheMessage() = runComposeUiTest {
        setContent { TembraTheme { EmptyState("Loops and sounds will appear here.") } }
        onNodeWithText("Loops and sounds will appear here.").assertIsDisplayed()
    }
}
