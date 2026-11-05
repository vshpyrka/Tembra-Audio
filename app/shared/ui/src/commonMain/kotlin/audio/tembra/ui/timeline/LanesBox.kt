package audio.tembra.ui.timeline

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The lanes box as the screen measured it (feature 012, spec FR-016, FR-021): the layout it was measured under and its height; both decide the mixer's anchors and strip form. */
internal data class LanesBox(
    val layout: TimelineLayout = TimelineLayout.Compact,
    val height: Dp = 0.dp,
)
