package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from design/stitch/theme.json → spacing and touchTargetMin; `seam` is the 1 px border of DESIGN.md. */
@Immutable
data class TembraSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val gutter: Dp = 12.dp,
    val pageMargin: Dp = 16.dp,
    val touchTargetMin: Dp = 44.dp,
    val seam: Dp = 1.dp,
    /** Reading-width cap for centred content on wide windows (plan: placeholder wide layout). */
    val wideContentMaxWidth: Dp = 720.dp,
    // Chrome metrics of the navigation frame (feature 002, data-model.md §6, spec FR-019).
    /** Top bar content height: `h-14` on the wide design screens. */
    val topBarHeight: Dp = 56.dp,
    /** Bottom bar content height: `h-16` on every phone bottom bar (the system inset is added below it). */
    val bottomBarHeight: Dp = 64.dp,
    /** Navigation rail width: `w-16`. */
    val railWidth: Dp = 64.dp,
    /** Supporting pane width: `w-80` inspector; DESIGN.md desktop layout. */
    val supportingPaneWidth: Dp = 320.dp,
    /** Bottom-bar item minimum width: `min-w-[56px]`. */
    val navItemMinWidth: Dp = 56.dp,
    /** Rail item width: rail `px-2` inside 64 dp. */
    val railItemWidth: Dp = 48.dp,
    /** Bottom-bar glyph size: `text-[20px]`. */
    val navBarIconSize: Dp = 20.dp,
    /** Rail glyph size: `text-[22px]`. */
    val navRailIconSize: Dp = 22.dp,
    /** Gap between a navigation glyph and its label: `gap-1`. */
    val navItemGap: Dp = 4.dp,
    /** Keyboard focus ring drawn in `primary` around a focused control (Constitution IV, spec FR-014). */
    val focusRingWidth: Dp = 2.dp,
)
