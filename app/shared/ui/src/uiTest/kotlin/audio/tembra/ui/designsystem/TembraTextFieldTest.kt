package audio.tembra.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Names, descriptions and search: the label, the enforced length and the error text with its semantics. */
@OptIn(ExperimentalTestApi::class)
class TembraTextFieldTest {

    private var value = ""

    private fun ComposeUiTest.setField(maxLength: Int = Int.MAX_VALUE, error: String? = null) {
        setContent {
            TembraTheme {
                var text by remember { mutableStateOf("") }
                TembraTextField(
                    value = text,
                    onValueChange = { text = it; value = it },
                    label = "Project name",
                    maxLength = maxLength,
                    error = error,
                    testTag = "field",
                )
            }
        }
    }

    @Test
    fun showsTheLabelAndReportsTypedText() = runComposeUiTest {
        setField()
        onNodeWithText("Project name").assertExists()
        onNodeWithTag("field").performTextInput("Demo")
        assertEquals("Demo", value)
        onNodeWithTag("field").assertTextContains("Demo")
    }

    @Test
    fun dropsAChangeBeyondMaxLength() = runComposeUiTest {
        setField(maxLength = 4)
        onNodeWithTag("field").performTextReplacement("Demo")
        assertEquals("Demo", value)
        onNodeWithTag("field").performTextReplacement("Demo project")
        assertEquals("Demo", value)
    }

    @Test
    fun errorTextRendersBelowWithErrorSemantics() = runComposeUiTest {
        setField(error = "Use 1 to 60 characters")
        onNodeWithTag("field.error").assertExists()
        onNodeWithText("Use 1 to 60 characters").assertExists()
        onNodeWithTag("field").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Use 1 to 60 characters"))
    }
}
