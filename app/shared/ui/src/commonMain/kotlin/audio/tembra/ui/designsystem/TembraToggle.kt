package audio.tembra.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import audio.tembra.ui.theme.TembraTheme

/**
 * The design's 32 × 16 dp switch (research R9, spec FR-024): a `secondary` track when on, `surfaceContainerHighest`
 * when off, a 12 dp `onSecondaryContainer` knob that slides over the motion token; hover lightens the track, keyboard
 * focus draws a `primary` ring; the drawn track sits inside a 44 dp toggleable target with `Switch` semantics.
 */
@Composable
fun TembraToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val offTrack = if (hovered) colors.surfaceBright else colors.surfaceContainerHighest
    val onTrack = if (hovered) lerp(colors.secondary, colors.onSecondary, 0.1f) else colors.secondary
    val track by animateColorAsState(
        targetValue = if (checked) onTrack else offTrack,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "toggleTrack",
    )
    val knobInset = (spacing.toggleHeight - spacing.toggleKnob) / 2
    val knobOffset by animateDpAsState(
        targetValue = if (checked) spacing.toggleWidth - spacing.toggleKnob - knobInset else knobInset,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "toggleKnob",
    )
    val knob = colors.onSecondaryContainer
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    Box(
        modifier = modifier
            .testTag(testTag)
            .toggleable(value = checked, interactionSource = interactionSource, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .testTag("$testTag.track")
                .size(spacing.toggleWidth, spacing.toggleHeight)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(color = track, cornerRadius = radius)
                    val knobSize = spacing.toggleKnob.toPx()
                    drawCircle(color = knob, radius = knobSize / 2, center = Offset(knobOffset.toPx() + knobSize / 2, size.height / 2))
                    if (focused) {
                        val width = ring.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-width, -width),
                            size = Size(size.width + width * 2, size.height + width * 2),
                            cornerRadius = CornerRadius(radius.x + width),
                            style = Stroke(width = width),
                        )
                    }
                },
        )
    }
}
