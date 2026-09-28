package audio.tembra.ui.tuner

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import audio.tembra.core.tuner.TargetString
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * One By Ear card (spec FR-017, FR-018, research R9): numeral, target frequency, note and octave and the Audition
 * button; while sounding the card is outlined in `secondary` with the design's glow, the button reads "Playing" and a
 * three-bar level indicator moves unless the platform asks for reduced motion (spec FR-027). Stacked cards are square
 * (or taller), wide cards at least 144 dp on `surfaceContainerLowest`, landscape cards fill their row with the
 * design's tighter padding.
 */
@Composable
internal fun StringCard(
    string: TargetString,
    playing: Boolean,
    layout: TunerLayout,
    onAudition: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val extras = TembraTheme.typographyExtras
    val gradients = TembraTheme.gradients
    val wide = layout == TunerLayout.Wide
    val shape = RoundedCornerShape(TembraShapes.medium)
    val fill = if (wide) colors.surfaceContainerLowest else colors.surfaceContainerLow
    val border = if (playing) colors.secondary else colors.outlineVariant.copy(alpha = BORDER_ALPHA)
    val glow = gradients.playingGlow
    val sizeModifier = when (layout) {
        TunerLayout.Stacked -> Modifier.squareOrTaller()
        TunerLayout.Wide -> Modifier.heightIn(min = spacing.stringCardHeightWide)
        TunerLayout.Landscape -> Modifier.fillMaxHeight()
    }
    val padding = when (layout) {
        TunerLayout.Stacked -> spacing.md
        TunerLayout.Wide -> spacing.md + spacing.xs / 2
        TunerLayout.Landscape -> spacing.sm
    }
    Column(
        modifier = modifier
            .testTag("tuner.card.${string.numeral}")
            .semantics { stateDescription = if (playing) TunerCopy.playing else "${string.numeral} ${string.note.name}" }
            .then(sizeModifier)
            .drawBehind {
                if (playing) {
                    val layers = 6
                    val blur = glow.radius.toPx()
                    for (layer in 1..layers) {
                        val spread = blur * layer / layers
                        drawRoundRect(
                            color = glow.color.copy(alpha = glow.color.alpha / layers),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2, size.height + spread * 2),
                            cornerRadius = CornerRadius(TembraShapes.medium.toPx() + spread),
                        )
                    }
                }
            }
            .background(fill, shape)
            .border(spacing.seam, border, shape)
            .padding(padding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = string.numeral,
                style = if (wide) TembraTheme.typography.labelMedium else extras.microLabel,
                fontWeight = FontWeight.Bold,
                color = colors.outline,
            )
            Text(text = "${formatHz(string.frequencyHz)} ${TunerCopy.hertz}", style = extras.tabularParam, color = colors.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.xs / 2)) {
            Text(text = string.note.pitchClass.label, style = if (wide) TembraTheme.typography.headlineLarge else extras.tunerCardNote, color = colors.onSurface)
            Text(text = string.note.octave.toString(), style = if (wide) TembraTheme.typography.headlineSmall else extras.tunerCardOctave, color = colors.primary)
            if (playing) {
                Spacer(modifier = Modifier.weight(1f))
                LevelBars()
            }
        }
        AuditionButton(string = string, playing = playing, onClick = if (playing) onStop else onAudition)
    }
}

@Composable
private fun AuditionButton(string: TargetString, playing: Boolean, onClick: () -> Unit) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(TembraShapes.default)
    val fill = when {
        playing -> colors.secondaryContainer.copy(alpha = BUTTON_ALPHA)
        hovered -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerHigh.copy(alpha = BUTTON_ALPHA)
    }
    val content = if (playing) colors.onSecondaryContainer else colors.onSurface
    val label = if (playing) TunerCopy.playing else TunerCopy.audition
    val description = if (playing) "${TunerCopy.stop} ${string.note.name}" else "${TunerCopy.audition} ${string.note.name}"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTargetMin)
            .testTag("tuner.card.${string.numeral}.audition")
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.cardButtonHeight)
                .background(fill, shape)
                .border(if (focused) spacing.focusRingWidth else spacing.seam, if (focused) colors.primary else colors.outlineVariant.copy(alpha = BORDER_ALPHA), shape),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TembraIcons.vector(if (playing) TembraIcon.Pause else TembraIcon.PlayArrow),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(spacing.segmentIconSmall),
            )
            Text(text = label, style = TembraTheme.typographyExtras.microLabel, color = content, maxLines = 1)
        }
    }
}

/** Three bars that breathe while a tone plays; still under reduce motion (spec FR-027). */
@Composable
private fun LevelBars() {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val animate = !TembraTheme.motion.reduceMotion
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "levelBars").animateFloat(0f, 1f, infiniteRepeatable(tween(LEVEL_MILLIS), RepeatMode.Reverse), label = "levelPhase")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs / 2), verticalAlignment = Alignment.Bottom, modifier = Modifier.testTag("tuner.card.level")) {
        for (bar in 0 until 3) {
            val base = 0.45f + 0.2f * bar
            Box(
                modifier = Modifier
                    .width(spacing.xs / 2 + spacing.xs / 4)
                    .height(spacing.lg)
                    .graphicsLayer {
                        val p = phase.value
                        val height = (base * (0.6f + 0.4f * (if (bar % 2 == 0) p else 1f - p))).coerceIn(0.2f, 1f)
                        scaleY = height
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .background(colors.secondary, RoundedCornerShape(TembraShapes.small)),
            )
        }
    }
}

/** A square card that grows taller when its content needs more room (large fonts), never shorter than its width. */
private fun Modifier.squareOrTaller(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minHeight = width.coerceAtMost(constraints.maxHeight)))
    val height = maxOf(width, placeable.height).coerceAtMost(constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, 0) }
}

private const val BORDER_ALPHA = 0.2f
private const val BUTTON_ALPHA = 0.6f
private const val LEVEL_MILLIS = 600
