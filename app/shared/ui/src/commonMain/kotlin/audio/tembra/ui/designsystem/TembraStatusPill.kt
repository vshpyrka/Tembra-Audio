package audio.tembra.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * The audio-state pill (research R9, spec FR-006, FR-027): `surfaceContainerLow`, a 6 dp dot in [color] that pulses
 * once a second unless the platform asks for reduced motion, an optional 14 dp glyph and the text drawn in capitals
 * in the tabular parameter style while the node announces the sentence-case [text]. With a [testTag], the dot carries
 * `<tag>.dot` and the glyph `<tag>.icon`.
 */
@Composable
fun TembraStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: TembraIcon? = null,
    pulsing: Boolean = false,
    testTag: String? = null,
) {
    val spacing = TembraTheme.spacing
    val animate = pulsing && !TembraTheme.motion.reduceMotion
    val alpha: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "statusPillPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "statusPillDot",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(
        modifier = modifier
            .testTagIfSet(testTag)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .background(TembraTheme.colors.surfaceContainerLow, CircleShape)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .testTagIfSet(testTag?.let { "$it.dot" })
                .size(spacing.statusDot)
                .graphicsLayer { this.alpha = alpha.value }
                .background(color, CircleShape),
        )
        if (icon != null) {
            Icon(
                imageVector = TembraIcons.vector(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.testTagIfSet(testTag?.let { "$it.icon" }).size(spacing.segmentIconSmall),
            )
        }
        Text(
            text = text.uppercase(),
            style = TembraTheme.typographyExtras.tabularParam,
            color = color,
            maxLines = 1,
        )
    }
}

private const val PULSE_MILLIS = 1000
