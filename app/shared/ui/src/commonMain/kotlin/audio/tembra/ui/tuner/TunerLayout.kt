package audio.tembra.ui.tuner

import androidx.window.core.layout.WindowSizeClass
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.theme.WindowWidth

/**
 * The form the tuner takes, derived from the window and never stored (spec FR-023, data-model.md §8): [Stacked] under
 * 600 dp and in the medium band of a tall window, [Wide] from 840 dp when the window is at least 480 dp tall, and
 * [Landscape] from 600 dp when it is shorter than that. The 480 dp line is window-core's medium height breakpoint,
 * the same one that keeps the rail without its panel in a short window (`LayoutMode`), so a phone held sideways shows
 * the rail beside the settings column as the Stitch landscape screens do.
 */
enum class TunerLayout {
    Stacked,
    Wide,
    Landscape,

    /** A foldable in tabletop posture: watched content above the fold, settings below it (feature 005, research R5). */
    Tabletop;

    companion object {
        fun from(width: WindowWidth, isShort: Boolean): TunerLayout = when (width) {
            WindowWidth.Compact -> Stacked
            WindowWidth.Medium -> if (isShort) Landscape else Stacked
            WindowWidth.Expanded -> if (isShort) Landscape else Wide
        }

        /** The layout of a measured window size class (breakpoints 600 dp, 840 dp wide and 480 dp tall; research R11). */
        fun from(windowSizeClass: WindowSizeClass): TunerLayout = from(
            width = WindowWidth.from(windowSizeClass),
            isShort = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        )

        /** [Tabletop] whenever the window has a tabletop [fold], at any size class; otherwise the window's band. */
        fun from(windowSizeClass: WindowSizeClass, fold: TabletopFold?): TunerLayout =
            if (fold != null) Tabletop else from(windowSizeClass)
    }
}
