package audio.tembra.ui.projects

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The card grid's form by width (spec FR-050): one column under 840 dp, two from 840, three from 1200. */
enum class ProjectsLayout(val columns: Int) {
    OneColumn(1),
    TwoColumns(2),
    ThreeColumns(3);

    /** Expanded cards draw the overview as a line (spec FR-054); compact ones as bars. */
    val overviewAsLine: Boolean get() = this != OneColumn

    companion object {
        private val TWO_MIN = 840.dp
        private val THREE_MIN = 1200.dp

        fun from(width: Dp): ProjectsLayout = when {
            width < TWO_MIN -> OneColumn
            width < THREE_MIN -> TwoColumns
            else -> ThreeColumns
        }
    }
}
