package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import audio.tembra.ui.theme.TembraTheme

/**
 * The handle of the app's sliders: a `primary` disc, always [TembraSliderDefaults.ThumbSize] across and centred in its
 * box, with a state layer on hover, press, drag and focus read from the slider's own [interactionSource], and the
 * focus ring. The box is the handle's touch target: `ThumbSize` wide and a touch target tall unless the caller's
 * [modifier] gives a larger one (it comes first on the root, so a caller's `Modifier.size` wins). The tempo slider
 * keeps the default; the noise gate makes it a touch target in both directions (feature 006, spec FR-018).
 */
@Composable
internal fun TembraSliderThumb(interactionSource: MutableInteractionSource, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val halo = hovered || pressed || dragged || focused
    val thumbColor = TembraTheme.colors.primary
    val ring = spacing.focusRingWidth
    Box(
        modifier
            .size(width = TembraSliderDefaults.ThumbSize, height = spacing.touchTargetMin)
            .drawBehind {
                val radius = TembraSliderDefaults.ThumbSize.toPx() / 2
                // Hover, press, drag and focus show a state layer around the disc; focus adds the ring.
                if (halo) drawCircle(thumbColor.copy(alpha = TembraSliderDefaults.THUMB_HALO_ALPHA), radius * TembraSliderDefaults.THUMB_HALO_SCALE)
                if (focused) drawCircle(thumbColor, radius + ring.toPx() * 2, style = Stroke(width = ring.toPx()))
                drawCircle(thumbColor, radius)
            },
    )
}
