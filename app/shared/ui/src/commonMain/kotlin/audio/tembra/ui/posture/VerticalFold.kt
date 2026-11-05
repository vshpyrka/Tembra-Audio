package audio.tembra.ui.posture

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * The hinge of a foldable half-opened like a book (feature 005, design iteration D1, research R8), a separating fold running from top to
 * bottom of the window. [left] and [right] are its edges in window pixels (equal for the emulator's zero-width fold).
 */
@Immutable
data class VerticalFold(val left: Float, val right: Float) {
    init {
        require(right >= left) { "The fold's right edge ($right) is left of its left edge ($left)" }
    }

    companion object {
        /** The first vertical, separating hinge of [posture], or null; iOS never reports one. */
        fun from(posture: Posture): VerticalFold? {
            val hinge = posture.hingeList.firstOrNull { it.isVertical && it.isSeparating } ?: return null
            return VerticalFold(left = hinge.bounds.left, right = hinge.bounds.right)
        }
    }
}

/** The current window's [VerticalFold], or null; recomposes on every fold change. */
@Composable
fun currentVerticalFold(): VerticalFold? = VerticalFold.from(currentWindowAdaptiveInfoV2().windowPosture)
