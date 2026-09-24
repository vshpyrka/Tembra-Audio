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
)
