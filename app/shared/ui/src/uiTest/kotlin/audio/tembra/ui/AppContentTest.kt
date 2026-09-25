package audio.tembra.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.devtools.DeveloperView
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test

/** A developer-view request replaces the shell; without one the shell renders (spec FR-022). */
@OptIn(ExperimentalTestApi::class)
class AppContentTest {

    @Test
    fun aRequestedDeveloperViewReplacesTheShell() = runComposeUiTest {
        setContent { TembraTheme { AppContent(DeveloperViewRequest(DeveloperView.TokenPreview)) { Text("shell") } } }
        onNodeWithTag("devtools.tokenPreview").assertIsDisplayed()
        onNodeWithText("shell").assertDoesNotExist()
    }

    @Test
    fun noRequestRendersTheShell() = runComposeUiTest {
        setContent { TembraTheme { AppContent(DeveloperViewRequest(null)) { Text("shell") } } }
        onNodeWithText("shell").assertIsDisplayed()
        onNodeWithTag("devtools.tokenPreview").assertDoesNotExist()
    }
}
