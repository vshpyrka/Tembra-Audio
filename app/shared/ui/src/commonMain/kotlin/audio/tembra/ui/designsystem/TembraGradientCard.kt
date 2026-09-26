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
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.RadialGlow
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The tuner's content card (research R9): 16 dp corners on `surfaceContainerLowest` with a 1 dp seam in
 * `outlineVariant` at 20 %, the screen's radial [glow] and the two blurred corner discs drawn as radial brushes
 * behind the content.
 */
@Composable
fun TembraGradientCard(
    glow: RadialGlow,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TembraTheme.colors
    val gradients = TembraTheme.gradients
    val shape = RoundedCornerShape(TembraShapes.large)
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .drawBehind {
                drawRect(brush = glow.brush(size))
                val radius = gradients.discRadius.toPx()
                val offset = gradients.discOffset.toPx()
                val topStart = Offset(-offset, -offset)
                drawCircle(
                    brush = Brush.radialGradient(listOf(gradients.discTopStart, Color.Transparent), center = topStart, radius = radius),
                    radius = radius,
                    center = topStart,
                )
                val bottomEnd = Offset(size.width + offset, size.height + offset)
                drawCircle(
                    brush = Brush.radialGradient(listOf(gradients.discBottomEnd, Color.Transparent), center = bottomEnd, radius = radius),
                    radius = radius,
                    center = bottomEnd,
                )
            }
            .border(TembraTheme.spacing.seam, colors.outlineVariant.copy(alpha = SEAM_ALPHA), shape),
        content = content,
    )
}

private const val SEAM_ALPHA = 0.2f
