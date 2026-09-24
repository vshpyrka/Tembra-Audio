package audio.tembra.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    }
}
