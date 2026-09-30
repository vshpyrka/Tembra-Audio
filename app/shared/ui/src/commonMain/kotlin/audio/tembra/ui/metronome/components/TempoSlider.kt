package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The tempo slider under the dial (research R8, R10): a Material [Slider] over 40–240 in whole numbers, restyled with
 * the design's 6 dp `surfaceContainerHighest` track and a 16 dp `primary` thumb, between the end labels "40" and
 * "240"; the thumb's box is 44 dp tall, so the slider is a whole touch target. The arrows come from Material; the page keys move the tempo by ten, as on the dial. The custom thumb keeps
 * Material's feedback: a `primary` state layer on hover, press, drag and focus, and the focus ring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoSlider(
    tempo: Tempo,
    onTempo: (Tempo) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "metronome.slider",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    val focused by source.collectIsFocusedAsState()
    val halo = hovered || pressed || dragged || focused
    val thumbColor = colors.primary
    val ring = spacing.focusRingWidth
    val labelStyle = TembraTheme.typographyExtras.tabularParam
    val announcement = MetronomeCopy.dialState(tempo, TempoMarking.of(tempo))
    val currentTempo by rememberUpdatedState(tempo)
    val currentOnTempo by rememberUpdatedState(onTempo)

    fun offer(next: Tempo) {
        if (next != currentTempo) currentOnTempo(next)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = Tempo.MIN.toString(), style = labelStyle, color = colors.outline)
        Slider(
            value = tempo.bpm.toFloat(),
            onValueChange = { offer(Tempo.clamped(it.roundToInt())) },
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
                // Announced like the dial, "120 beats per minute, Moderato", not Material's "120.0" (spec FR-030).
                .semantics { this.stateDescription = announcement }
                .onPreviewKeyEvent { event ->
                    val delta = when {
                        event.type != KeyEventType.KeyDown -> 0
                        event.key == Key.PageUp -> MetronomeDefaults.PAGE_STEP
                        event.key == Key.PageDown -> -MetronomeDefaults.PAGE_STEP
                        else -> 0
                    }
                    if (delta != 0) offer(currentTempo.plus(delta))
                    delta != 0
                },
            valueRange = Tempo.MIN.toFloat()..Tempo.MAX.toFloat(),
            steps = Tempo.MAX - Tempo.MIN - 1,
            interactionSource = source,
            thumb = {
                // The drawn thumb is 16 dp; its box is a touch target tall, which makes the slider (and its node) 44 dp.
                Box(
                    Modifier
                        .size(width = MetronomeDefaults.SliderThumbSize, height = spacing.touchTargetMin)
                        .drawBehind {
                            val radius = size.width / 2
                            // Hover, press, drag and focus show a state layer around the thumb; focus adds the ring.
                            if (halo) drawCircle(thumbColor.copy(alpha = MetronomeDefaults.THUMB_HALO_ALPHA), radius * MetronomeDefaults.THUMB_HALO_SCALE)
                            if (focused) {
                                drawCircle(thumbColor, radius + ring.toPx() * 2, style = Stroke(width = ring.toPx()))
                            }
                            drawCircle(thumbColor, radius)
                        },
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MetronomeDefaults.SliderTrackHeight)
                        .background(colors.surfaceContainerHighest, CircleShape),
                )
            },
        )
        Text(text = Tempo.MAX.toString(), style = labelStyle, color = colors.outline)
    }
}
