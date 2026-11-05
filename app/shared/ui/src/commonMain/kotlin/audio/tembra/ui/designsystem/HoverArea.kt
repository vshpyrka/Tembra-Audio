package audio.tembra.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.theme.TembraTheme

/**
 * The hover state over a control's whole clickable area (feature 011, spec FR-016a, research R20): place it on the
 * clickable element, outside its 44 dp minimum-size padding, so the tint covers exactly the area a click would hit,
 * not only the smaller shape drawn inside it. Drawn over the content, as Material's state layer is, so it shows on the
 * control's own surface too. [cornerRadius] rounds it like the control's own shape.
 */
@Composable
internal fun Modifier.hoverArea(hovered: Boolean, cornerRadius: Dp): Modifier {
    if (!hovered) return this
    val tint = TembraTheme.colors.onSurface.copy(alpha = HOVER_AREA_ALPHA)
    return drawWithContent {
        drawContent()
        drawRoundRect(color = tint, cornerRadius = CornerRadius(cornerRadius.toPx()))
    }
}

/** The tint's opacity: Material's hover state layer. */
private const val HOVER_AREA_ALPHA = 0.08f
