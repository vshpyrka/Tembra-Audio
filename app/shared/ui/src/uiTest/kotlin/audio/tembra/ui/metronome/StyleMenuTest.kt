package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.ui.metronome.components.StyleMenu
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone-landscape style menu (T152, contracts/design-system-additions.md): a pill naming the style that opens the
 * three styles with a check on the current one; its name, state, entries, selection and the hand-edited case.
 */
@OptIn(ExperimentalTestApi::class)
class StyleMenuTest {

    private val chosen = mutableListOf<MetronomeStyle>()
    private val ids = MetronomeStyle.entries.map { it.id }

    private fun ComposeUiTest.setMenu(selected: MetronomeStyle?) {
        setContent {
            TembraTheme {
                Box(Modifier.size(320.dp).background(TembraTheme.colors.surface), contentAlignment = Alignment.TopStart) {
                    StyleMenu(selected = selected, onSelect = { chosen += it })
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.assertState(state: String) {
        onNodeWithTag("metronome.style").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
    }

    @Test
    fun thePillNamesTheStyleAndIsA44dpButton() = runComposeUiTest {
        setMenu(MetronomeStyle.Standard)
        onNodeWithTag("metronome.style")
            .assertContentDescriptionEquals("Style, Standard")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
        onNodeWithTag("metronome.style.label", useUnmergedTree = true).assertExists()
        assertState("Collapsed")
        for (id in ids) onNodeWithTag("metronome.style.$id").assertDoesNotExist()
    }

    @Test
    fun theMenuChecksTheCurrentStyleAndSelectsAnother() = runComposeUiTest {
        setMenu(MetronomeStyle.Standard)
        onNodeWithTag("metronome.style").performClick()
        waitForIdle()
        assertState("Expanded")
        onNodeWithTag("metronome.style.standard").assertIsSelected()
        onNodeWithTag("metronome.style.standard.check", useUnmergedTree = true).assertExists()
        for (id in ids.drop(1)) onNodeWithTag("metronome.style.$id").assertIsNotSelected()
        val tops = ids.map { onNodeWithTag("metronome.style.$it").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the styles are not in order")
        onNodeWithTag("metronome.style.syncopated").performClick()
        waitForIdle()
        assertEquals(listOf(MetronomeStyle.Syncopated), chosen)
        assertState("Collapsed")
        // The current style selects nothing again.
        onNodeWithTag("metronome.style").performClick()
        waitForIdle()
        onNodeWithTag("metronome.style.standard").performClick()
        waitForIdle()
        assertEquals(listOf(MetronomeStyle.Syncopated), chosen)
    }

    /** Cells edited by hand match no style: the pill reads "Style" and no entry is checked. */
    @Test
    fun noStyleReadsNoneAndChecksNothing() = runComposeUiTest {
        setMenu(null)
        onNodeWithTag("metronome.style").assertContentDescriptionEquals("Style, none")
        onNodeWithTag("metronome.style").performClick()
        waitForIdle()
        for (id in ids) {
            onNodeWithTag("metronome.style.$id").assertIsNotSelected()
            onNodeWithTag("metronome.style.$id.check", useUnmergedTree = true).assertDoesNotExist()
        }
        onNodeWithTag("metronome.style.downbeat").performClick()
        waitForIdle()
        assertEquals(listOf(MetronomeStyle.Downbeat), chosen)
    }
}
