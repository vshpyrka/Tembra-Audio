package audio.tembra.ui.metronome

import androidx.window.core.layout.WindowSizeClass
import audio.tembra.ui.theme.WindowWidth

/**
 * The form the metronome takes, derived from the window and never stored (data-model.md §9): the tuner's bands.
 * [Stacked] under 600 dp and in the medium band of a tall window, [Wide] from 840 dp when the window is at least
 * 480 dp tall, and [Landscape] from 600 dp when it is shorter than that.
 */
enum class MetronomeLayout {
    Stacked,
    Wide,
    Landscape;

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
    }
}
