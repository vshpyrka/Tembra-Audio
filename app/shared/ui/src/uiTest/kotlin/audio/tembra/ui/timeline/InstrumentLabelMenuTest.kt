package audio.tembra.ui.timeline

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.InstrumentLabelMenu
import audio.tembra.ui.timeline.components.LABEL_MENU_TAG
import audio.tembra.ui.timeline.components.LABEL_NONE
import kotlin.test.Test
import kotlin.test.assertEquals

/** The instrument menu lists the twelve labels and None (spec FR-021; T092). */
@OptIn(ExperimentalTestApi::class)
class InstrumentLabelMenuTest {

    private val selected = mutableListOf<InstrumentLabel?>()

    private fun ComposeUiTest.setMenu() {
        setContent {
            TembraTheme {
                InstrumentLabelMenu(expanded = true, onDismiss = {}, onSelect = { selected += it }) { Text("Label") }
            }
        }
    }

    @Test
    fun listsNoneAndEveryLabelInOrder() = runComposeUiTest {
        setMenu()
        onNodeWithTag("$LABEL_MENU_TAG.$LABEL_NONE").assertExists()
        for (label in InstrumentLabel.entries) {
            onNodeWithTag("$LABEL_MENU_TAG.${label.serialName}").assertExists()
            onNodeWithText(label.displayName).assertExists()
        }
        assertEquals(12, InstrumentLabel.entries.size)
    }

    @Test
    fun choosingALabelOrNoneReportsIt() = runComposeUiTest {
        setMenu()
        onNodeWithTag("$LABEL_MENU_TAG.guitar").performClick()
        onNodeWithTag("$LABEL_MENU_TAG.$LABEL_NONE").performClick()
        assertEquals(listOf(InstrumentLabel.Guitar, null), selected)
    }
}
