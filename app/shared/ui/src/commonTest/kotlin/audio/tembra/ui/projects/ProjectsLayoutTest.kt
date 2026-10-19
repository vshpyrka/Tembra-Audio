package audio.tembra.ui.projects

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The card grid's columns by width (spec FR-050; T117). */
class ProjectsLayoutTest {

    @Test
    fun oneColumnUnder840TwoFrom840ThreeFrom1200() {
        assertEquals(ProjectsLayout.OneColumn, ProjectsLayout.from(360.dp))
        assertEquals(ProjectsLayout.OneColumn, ProjectsLayout.from(839.dp))
        assertEquals(ProjectsLayout.TwoColumns, ProjectsLayout.from(840.dp))
        assertEquals(ProjectsLayout.TwoColumns, ProjectsLayout.from(1199.dp))
        assertEquals(ProjectsLayout.ThreeColumns, ProjectsLayout.from(1200.dp))
        assertEquals(1, ProjectsLayout.OneColumn.columns)
        assertEquals(3, ProjectsLayout.ThreeColumns.columns)
    }

    @Test
    fun theOverviewIsBarsOnOneColumnAndALineOnWiderGrids() {
        assertFalse(ProjectsLayout.OneColumn.overviewAsLine)
        assertTrue(ProjectsLayout.TwoColumns.overviewAsLine)
        assertTrue(ProjectsLayout.ThreeColumns.overviewAsLine)
    }
}
