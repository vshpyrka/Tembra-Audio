package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.GridSize
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.GridPicker
import kotlin.test.Test
import kotlin.test.assertEquals

/** The grid pill (spec FR-034; T099, US3): the five sizes, the current one described, a choice reported once. */
@OptIn(ExperimentalTestApi::class)
class GridPickerTest {

    @Test
    fun theMenuListsEverySizeAndReportsTheChoice() = runComposeUiTest {
        val chosen = mutableListOf<GridSize>()
        setContent { TembraTheme { GridPicker(grid = GridSize.Sixteenth, onGrid = { chosen += it }) } }
        onNodeWithTag(TimelineTestTags.GRID).assertContentDescriptionEquals("Grid 1/16").performClick()
        for (size in GridSize.entries) onNodeWithTag("${TimelineTestTags.GRID}.${size.name.lowercase()}").assertExists()
        onNodeWithTag("${TimelineTestTags.GRID}.bar").performClick()
        assertEquals(listOf(GridSize.Bar), chosen)
    }
}
