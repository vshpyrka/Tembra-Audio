package audio.tembra.ui.posture

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * The fold of a window in tabletop posture: a foldable half-opened with a horizontal fold across the window (spec
 * FR-001, research R1). [top] and [bottom] are the fold's edges in window pixels; they are equal for a fold with no
 * height (the emulator's). Derived from the window on every composition, never stored.
 */
@Immutable
data class TabletopFold(val top: Float, val bottom: Float) {
    init {
        require(bottom >= top) { "The fold's bottom ($bottom) is above its top ($top)" }
    }

    companion object {
        /**
         * The fold of [posture] when it is tabletop: its first horizontal, separating hinge. Any other posture, and a
         * tabletop posture without such a hinge, is null. iOS always reports the default posture, so it is never
         * tabletop there.
         */
        fun from(posture: Posture): TabletopFold? {
            if (!posture.isTabletop) return null
            val hinge = posture.hingeList.firstOrNull { !it.isVertical && it.isSeparating } ?: return null
            return TabletopFold(top = hinge.bounds.top, bottom = hinge.bounds.bottom)
        }
    }
}

/** The current window's [TabletopFold], or null when it is not in tabletop posture; recomposes on every fold change. */
@Composable
fun currentTabletopFold(): TabletopFold? = TabletopFold.from(currentWindowAdaptiveInfo().windowPosture)
