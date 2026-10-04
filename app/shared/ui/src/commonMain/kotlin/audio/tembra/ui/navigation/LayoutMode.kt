package audio.tembra.ui.navigation

import androidx.window.core.layout.WindowSizeClass
import audio.tembra.ui.theme.WindowWidth

/**
 * The form the frame takes, derived from the window and never stored (data-model.md §3, spec FR-004 to FR-006):
 * a bottom bar under 600 dp, a rail from 600 dp, and the rail with the supporting pane from 840 dp unless the window
 * is shorter than 480 dp.
 */
enum class LayoutMode {
    BottomBar,
    Rail,
    RailWithPanel,

    /**
     * Experiment (not in any spec): the iPhone Duo's closed outer display, where iOS keeps the status bar and camera in
     * a column at the end edge and Apple's guidance puts the tab bar in that column too (docs/research/iphone-duo.md).
     * The rail fills that column with its items at the bottom, and no bottom bar takes vertical space.
     */
    EndRail;

    companion object {
        fun from(width: WindowWidth, isShort: Boolean): LayoutMode = when (width) {
            WindowWidth.Compact -> BottomBar
            WindowWidth.Medium -> Rail
            WindowWidth.Expanded -> if (isShort) Rail else RailWithPanel
        }

        /** Short means under window-core's medium height breakpoint (480 dp). */
        fun from(windowSizeClass: WindowSizeClass): LayoutMode = from(
            width = WindowWidth.from(windowSizeClass),
            isShort = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        )

        /** As [from], with [EndRail] in place of [BottomBar] on the iPhone Duo (the experiment). */
        fun from(windowSizeClass: WindowSizeClass, iphoneDuo: Boolean): LayoutMode {
            val mode = from(windowSizeClass)
            return if (iphoneDuo && mode == BottomBar) EndRail else mode
        }
    }
}
