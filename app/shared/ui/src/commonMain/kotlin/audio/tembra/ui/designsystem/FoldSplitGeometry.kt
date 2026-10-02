package audio.tembra.ui.designsystem

import audio.tembra.ui.posture.TabletopFold
import kotlin.math.roundToInt

/**
 * Where [FoldSplit]'s two regions fall (data-model.md §3): the upper one from the layout's top to [gutter] above the
 * fold, the lower one from [gutter] below the fold to the layout's bottom, with the fold taken from window pixels into
 * the layout by its top in the window. Without a fold, or before the layout has been placed, the regions share the
 * height around a gap of two gutters. Both are clamped into the layout, so a fold above or below it gives one region
 * everything.
 */
internal object FoldSplitGeometry {

    /** The upper region's height and the lower region's top, in px from the layout's top. */
    data class Regions(val upperHeight: Int, val lowerTop: Int) {
        fun lowerHeight(height: Int): Int = height - lowerTop
    }

    fun regions(height: Int, originY: Float, fold: TabletopFold?, gutter: Int): Regions {
        if (fold == null || originY.isNaN()) {
            val upper = ((height - gutter * 2) / 2).coerceIn(0, height)
            return Regions(upperHeight = upper, lowerTop = (upper + gutter * 2).coerceIn(upper, height))
        }
        val upper = (fold.top - originY - gutter).roundToInt().coerceIn(0, height)
        val lowerTop = (fold.bottom - originY + gutter).roundToInt().coerceIn(upper, height)
        return Regions(upperHeight = upper, lowerTop = lowerTop)
    }
}
