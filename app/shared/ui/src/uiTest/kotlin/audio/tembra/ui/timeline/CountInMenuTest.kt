package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.CountIn
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.CountInMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/** The count-in row of the metronome sheet (feature 010, spec FR-015, FR-018; T062): the three values, the choice, read-only during a take. */
@OptIn(ExperimentalTestApi::class)
class CountInMenuTest {

    private val chosen = mutableListOf<CountIn>()

    @Test
    fun theThreeValuesAreNamedAndAChoiceIsReported() = runComposeUiTest {
        setContent { TembraTheme { CountInMenu(value = CountIn.ONE_BAR, enabled = true, onSelect = { chosen += it }) } }
        onNodeWithTag(TimelineTestTags.COUNT_IN).assertContentDescriptionEquals("Count-in, 1 bar").assertIsEnabled().performClick()
        onNodeWithTag("${TimelineTestTags.COUNT_IN}.${CountIn.TWO_BARS.name}").performClick()
        assertEquals(listOf(CountIn.TWO_BARS), chosen)
    }

    @Test
    fun offAndTwoBarsReadAsSuch() = runComposeUiTest {
        setContent {
            TembraTheme {
                CountInMenu(value = CountIn.OFF, enabled = true, onSelect = {})
            }
        }
        onNodeWithTag(TimelineTestTags.COUNT_IN).assertContentDescriptionEquals("Count-in, Off")
    }

    @Test
    fun twoBarsReadsAsSuch() = runComposeUiTest {
        setContent { TembraTheme { CountInMenu(value = CountIn.TWO_BARS, enabled = true, onSelect = {}) } }
        onNodeWithTag(TimelineTestTags.COUNT_IN).assertContentDescriptionEquals("Count-in, 2 bars")
    }

    @Test
    fun theRowIsReadOnlyWhileATakeRuns() = runComposeUiTest {
        setContent { TembraTheme { CountInMenu(value = CountIn.ONE_BAR, enabled = false, onSelect = { chosen += it }) } }
        onNodeWithTag(TimelineTestTags.COUNT_IN).assertIsNotEnabled().performClick()
        onNodeWithTag("${TimelineTestTags.COUNT_IN}.${CountIn.OFF.name}").assertDoesNotExist()
        assertEquals(emptyList(), chosen)
    }
}
