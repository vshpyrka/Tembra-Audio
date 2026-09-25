package audio.tembra.ui.devtools

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.TembraTypeScale
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DesignTokenPreviewTest {

    private fun hasTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    @Test
    fun rendersEverySchemeAndDawColorAsALabelledSwatch() = runComposeUiTest {
        setContent { TembraTheme { DesignTokenPreview() } }
        onNodeWithTag("swatch.primary", useUnmergedTree = true).assertContentDescriptionEquals("primary #CEBDFF")
        onNodeWithTag("swatch.playhead", useUnmergedTree = true).assertContentDescriptionEquals("playhead #C084FC")
        onAllNodes(hasTagPrefix("swatch."), useUnmergedTree = true).assertCountEquals(47 + 18)
    }

    @Test
    fun rendersOneSampleLinePerTextStyle() = runComposeUiTest {
        setContent { TembraTheme { DesignTokenPreview() } }
        for (name in TembraTypeScale.byThemeName.keys) {
            onNodeWithTag("type.$name", useUnmergedTree = true).assertExists()
        }
        onAllNodes(hasTagPrefix("type."), useUnmergedTree = true).assertCountEquals(12)
    }
}
