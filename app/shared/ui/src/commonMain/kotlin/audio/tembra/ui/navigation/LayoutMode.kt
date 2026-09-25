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
    RailWithPanel;

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
    }
}
