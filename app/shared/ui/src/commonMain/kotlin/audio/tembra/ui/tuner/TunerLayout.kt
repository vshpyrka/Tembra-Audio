package audio.tembra.ui.tuner

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    Tabletop,

    /**
     * The form of a screen too short for [Stacked] (2026-10-16): instrument and mode on one row (the audio-state pill,
     * which only reports, is left out), which gives the Auto Listen or By Ear card under it the 60 dp the second row
     * took; the cards are [Stacked]'s. From about 407 dp of content the compact meter (243 dp at its least width) is
     * whole under the row; shorter screens still scroll it, as the meter cannot shrink further without a smaller form.
     */
    Pocket;

    /** [Pocket] in place of [Stacked] when the screen's content is under [PocketBelow] tall; every other form stays. */
    fun forContentHeight(height: Dp): TunerLayout = if (this == Stacked && height < PocketBelow) Pocket else this

    companion object {
        /**
         * The content height under which [Stacked] gives way to [Pocket]: the metronome's line (the tablet survey,
         * 2026-10-16, never saw the stacked meter whole at 212 dp of content and lost its noise gate at 407 dp, both
         * under the shell's former 56 dp top bar; the line moved with the bar from 450 dp, so the same windows take it).
         */
        val PocketBelow: Dp = 506.dp

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
