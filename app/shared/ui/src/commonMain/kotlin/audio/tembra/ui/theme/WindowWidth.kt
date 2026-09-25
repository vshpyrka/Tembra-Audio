package audio.tembra.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/** Width buckets of the Material window size classes, computed from the measured window (Constitution IV). */
enum class WindowWidth {
    Compact,
    Medium,
    Expanded;

    val isWide: Boolean get() = this != Compact

    companion object {
        private val MEDIUM_MIN = 600.dp
        private val EXPANDED_MIN = 840.dp

        fun fromWidth(width: Dp): WindowWidth = when {
            width < MEDIUM_MIN -> Compact
            width < EXPANDED_MIN -> Medium
            else -> Expanded
        }

        /** The bucket of a measured window size class (window-core breakpoints 600 dp and 840 dp; research R3). */
        fun from(windowSizeClass: WindowSizeClass): WindowWidth = when {
            !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> Compact
            !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> Medium
            else -> Expanded
        }
    }
}
