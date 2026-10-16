package audio.tembra.ui.metronome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
     * The dial-first form for a screen too short for the [Stacked] card over the dial: the accent cells over the dial
     * card, which fills the height, the tap and play row under it, and the other settings in a sheet. Born as the
     * iPhone Duo experiment (its closed screen); since 2026-10-16 also any [Stacked] screen under [PocketBelow] of
     * content (a short desktop window, a split-screen half), where the dial gives way to a tempo row in the shortest.
     */
    Pocket;

    /** [Pocket] in place of [Stacked] when the screen's content is under [PocketBelow] tall; every other form stays. */
    fun forContentHeight(height: Dp): MetronomeLayout = if (this == Stacked && height < PocketBelow) Pocket else this

    companion object {
        /**
         * The content height under which [Stacked] gives way to [Pocket]. The tablet survey (2026-10-16) found the
         * dial under the fold in a 772 × 511 dp desktop window and whole in a 700 × 708 dp one; the line was 450 dp
         * under the shell's 56 dp top bar and moved with the bar's removal (the same day) to 506 dp, so the same
         * windows take [Pocket]: the 772 × 511 window (463 dp now) does, a 360 × 720 dp phone held upright with
         * three-button navigation (584 dp) keeps [Stacked]. Stacked fits whole from 618 dp at 4/4; under that it scrolls.
         */
        val PocketBelow: Dp = 506.dp

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
