package audio.tembra.ui.metronome

import androidx.window.core.layout.WindowSizeClass
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.theme.WindowWidth

/**
 * The form the metronome takes, derived from the window and never stored (data-model.md §9): the tuner's bands.
 * [Stacked] under 600 dp and in the medium band of a tall window, [Wide] from 840 dp when the window is at least
 * 480 dp tall, and [Landscape] from 600 dp when it is shorter than that.
 */
enum class MetronomeLayout {
    Stacked,
    Wide,
    Landscape,

    /** A foldable in tabletop posture: watched content above the fold, settings below it (feature 005, research R5). */
    Tabletop,

    /**
     * Experiment (not in any spec): the iPhone Duo's closed screen, too short for the [Stacked] card over the dial.
     * Only the accent cells sit over the dial card, which fills the height; the other settings open in a sheet.
     */
    Pocket;

    companion object {
        fun from(width: WindowWidth, isShort: Boolean): MetronomeLayout = when (width) {
            WindowWidth.Compact -> Stacked
            WindowWidth.Medium -> if (isShort) Landscape else Stacked
            WindowWidth.Expanded -> if (isShort) Landscape else Wide
        }

        /** The layout of a measured window size class (breakpoints 600 dp and 840 dp wide, 480 dp tall). */
        fun from(windowSizeClass: WindowSizeClass): MetronomeLayout = from(
            width = WindowWidth.from(windowSizeClass),
            isShort = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        )

        /** [Tabletop] whenever the window has a tabletop [fold], at any size class; otherwise the window's band. */
        fun from(windowSizeClass: WindowSizeClass, fold: TabletopFold?): MetronomeLayout =
            if (fold != null) Tabletop else from(windowSizeClass)

        /** As [from], with [Pocket] in place of a compact-width [Stacked] form on the iPhone Duo (the experiment). */
        fun from(windowSizeClass: WindowSizeClass, fold: TabletopFold?, iphoneDuo: Boolean): MetronomeLayout {
            val layout = from(windowSizeClass, fold)
            val compact = WindowWidth.from(windowSizeClass) == WindowWidth.Compact
            return if (iphoneDuo && compact && layout == Stacked) Pocket else layout
        }
    }
}
