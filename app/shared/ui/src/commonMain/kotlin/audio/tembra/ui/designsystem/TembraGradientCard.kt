package audio.tembra.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import audio.tembra.ui.theme.LinearGlow
import audio.tembra.ui.theme.RadialGlow
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The tuner's and the metronome's content card (research R9 of feature 003, R10 of feature 004): 16 dp corners on
 * `surfaceContainerLowest` with a 1 dp seam in `outlineVariant` at 20 %, the screen's radial [glow], the two blurred
 * corner discs placed by [discs] and an optional [overlay] (the dial card's vertical tint), all drawn as brushes behind
 * the content in that order.
 */
@Composable
fun TembraGradientCard(
    glow: RadialGlow,
    modifier: Modifier = Modifier,
    discs: CardDiscs = CardDiscs.TopStartBottomEnd,
    overlay: LinearGlow? = null,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TembraTheme.colors
    val gradients = TembraTheme.gradients
    val shape = RoundedCornerShape(TembraShapes.large)
    Box(
        modifier = modifier
            .testTagIfSet(testTag)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .drawBehind {
                drawRect(brush = glow.brush(size))
                val radius = gradients.discRadius.toPx()
                val offset = gradients.discOffset.toPx()
                val start = -offset
                val top = -offset
                val end = size.width + offset
                val bottom = size.height + offset
                when (discs) {
                    CardDiscs.TopStartBottomEnd -> {
                        drawDisc(gradients.discTopStart, Offset(start, top), radius)
                        drawDisc(gradients.discBottomEnd, Offset(end, bottom), radius)
                    }
                    CardDiscs.TopEndBottomStart -> {
                        drawDisc(gradients.discBottomEnd, Offset(end, top), radius)
                        drawDisc(gradients.discTopStart, Offset(start, bottom), radius)
                    }
                    CardDiscs.None -> Unit
                }
                if (overlay != null) drawRect(brush = overlay.brush(size))
            }
            .border(TembraTheme.spacing.seam, colors.outlineVariant.copy(alpha = SEAM_ALPHA), shape),
        content = content,
    )
}

/** One blurred disc: [color] fading to transparent over [radius] around [center]. */
private fun DrawScope.drawDisc(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

private const val SEAM_ALPHA = 0.2f
