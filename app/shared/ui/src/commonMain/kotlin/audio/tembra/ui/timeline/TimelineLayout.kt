package audio.tembra.ui.timeline

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.navigation.LayoutMode

/**
 * The form the Timeline takes (contracts/timeline-screen.md "Layouts"), derived from the window and never stored:
 * [Compact] under 600 dp wide, [Medium] from 600 to 839 dp, [Landscape] from 600 dp wide and under 480 dp tall (a phone
 * held sideways), [Expanded] from 840 dp wide and 480 dp tall. The iPhone Duo's end rail ([LayoutMode.EndRail]) is a
 * compact window whatever its size.
 */
enum class TimelineLayout {
    Compact,
    Medium,
    Landscape,
    Expanded;

    /** The expanded layout pins a 200 dp header column; the others keep the header inside the lane row. */
    val pinnedHeaders: Boolean get() = this == Expanded

    /** Track settings open inline under the header on expanded windows, as a nested screen elsewhere. */
    val inlineSettings: Boolean get() = this == Expanded

    companion object {
        private val MEDIUM_MIN = 600.dp
        private val EXPANDED_MIN = 840.dp
        private val SHORT_MAX = 480.dp

        fun from(width: Dp, height: Dp, layoutMode: LayoutMode): TimelineLayout = when {
            layoutMode == LayoutMode.EndRail -> Compact
            width < MEDIUM_MIN -> Compact
            height < SHORT_MAX -> Landscape
            width < EXPANDED_MIN -> Medium
            else -> Expanded
        }
    }
}
