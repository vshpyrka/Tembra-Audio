package audio.tembra.ui.timeline

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.GridSize
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.SnapSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/** The snap sheet (spec FR-018, D1; T139): titled and explained, the five sizes with the current one checked, a choice reported then dismissed. */
@OptIn(ExperimentalTestApi::class)
class SnapSheetTest {

    private val calls = mutableListOf<String>()

    private fun ComposeUiTest.setSheet(grid: GridSize) {
        setContent {
            TembraTheme {
                SnapSheet(grid = grid, onGrid = { calls += "grid(${it.name})" }, onDismiss = { calls += "dismiss" })
            }
        }
        waitForIdle()
    }

    @Test
    fun theSheetIsTitledAndSaysWhatSnaps() = runComposeUiTest {
        setSheet(GridSize.Sixteenth)
        onNodeWithTag(TimelineTestTags.SNAP_SHEET).assertExists()
        onNodeWithText(TimelineCopy.snapToGrid).assertIsDisplayed()
        onNodeWithText(TimelineCopy.snapCaption).assertIsDisplayed()
    }

    @Test
    fun listsTheFiveSizesWithOnlyTheCurrentOneSelectedAndChecked() = runComposeUiTest {
        setSheet(GridSize.Quarter)
        for (size in GridSize.entries) {
            val current = size == GridSize.Quarter
            val row = onNodeWithTag(TimelineTestTags.snapSheetRow(size)).assertIsDisplayed().assertTextEquals(size.label).assertHasClickAction()
            if (current) row.assertIsSelected() else row.assertIsNotSelected()
            val check = onNodeWithTag(TimelineTestTags.snapSheetCheck(size), useUnmergedTree = true)
            if (current) check.assertIsDisplayed() else check.assertDoesNotExist()
        }
    }

    @Test
    fun aRowReportsTheChoiceThenDismissesTheSheet() = runComposeUiTest {
        setSheet(GridSize.Sixteenth)
        onNodeWithTag(TimelineTestTags.snapSheetRow(GridSize.Bar)).performClick()
        waitForIdle()
        assertEquals(listOf("grid(Bar)", "dismiss"), calls)
    }

    @Test
    fun theCurrentRowAlsoReportsAndDismisses() = runComposeUiTest {
        setSheet(GridSize.Off)
        onNodeWithTag(TimelineTestTags.snapSheetRow(GridSize.Off)).performClick()
        waitForIdle()
        assertEquals(listOf("grid(Off)", "dismiss"), calls)
    }
}
