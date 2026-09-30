package audio.tembra.ui.metronome.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * Play and pause (research R10): a 64 dp round `secondaryContainer` button (hover `secondary`) with the play or pause
 * glyph (filled, as the design) and the `playGlow` drawn around it as a radial brush, never a shadow. While [running] the glow breathes; it is
 * static under reduce motion. The glyph node carries `<testTag>.icon.<glyph>` so tests can tell the two apart.
 */
@Composable
internal fun PlayPauseButton(
    running: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "metronome.play",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val glow = TembraTheme.gradients.playGlow
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pulse = rememberGlowPulse(active = running && !TembraTheme.motion.reduceMotion)
    val fill = if (hovered) colors.secondary else colors.secondaryContainer
    val content = if (hovered) colors.onSecondary else colors.onSecondaryContainer
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth
    val icon = if (running) TembraIcon.PauseFilled else TembraIcon.PlayArrowFilled

    Box(
        modifier = modifier
            .testTag(testTag)
            .size(spacing.playButton)
            .drawBehind {
                val radius = size.minDimension / 2
                val reach = radius + glow.radius.toPx()
                val strength = pulse?.value ?: 1f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to glow.color.copy(alpha = glow.color.alpha * strength),
                        radius / reach to glow.color.copy(alpha = glow.color.alpha * strength),
                        1f to Color.Transparent,
                        center = center,
                        radius = reach,
                    ),
                    radius = reach,
                )
                drawCircle(color = fill, radius = radius)
                if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(radius))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (running) MetronomeCopy.stopMetronome else MetronomeCopy.startMetronome },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TembraIcons.vector(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(MetronomeDefaults.PlayIconSize).testTag("$testTag.icon.${icon.name}"),
        )
    }
}

/** The glow's strength, breathing between [MetronomeDefaults.GLOW_PULSE_MIN] and 1 while [active]; null (full, static) otherwise. */
@Composable
private fun rememberGlowPulse(active: Boolean): State<Float>? {
    if (!active) return null
    val transition = rememberInfiniteTransition(label = "playGlow")
    return transition.animateFloat(
        initialValue = MetronomeDefaults.GLOW_PULSE_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MetronomeDefaults.GLOW_PULSE_MILLIS), RepeatMode.Reverse),
        label = "playGlowStrength",
    )
}
