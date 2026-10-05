package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The noise gate control of Auto Listen (feature 006, spec FR-001, FR-002; contracts/tuner-screen.md §2): a label over
 * a Material [Slider] between the end labels "Off" and "High". The handle's place is the gate level, in the steps of
 * [NoiseGate]; the start of the track is off. It is built like the metronome's tempo slider and shares its handle
 * ([TembraSliderThumb]), here in a touch-target box in both directions (spec FR-018). [onGate] is raised for every
 * step the handle reaches and [onGateSettled] when the move has ended; dragging, a touch on the track and the arrow
 * keys all come from Material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoiseGateSlider(
    gate: NoiseGate,
    onGate: (NoiseGate) -> Unit,
    onGateSettled: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "tuner.noiseGate",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val endLabelStyle = TembraTheme.typographyExtras.tabularParam
    val currentGate by rememberUpdatedState(gate)
    val currentOnGate by rememberUpdatedState(onGate)

    Column(modifier = modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = TunerCopy.noiseGate.uppercase(),
            style = TembraTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // No gap between an end label and the slider: the handle's box already reaches 14 dp past each end of the
        // track, which is the distance the tempo slider keeps between its labels and its track.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = TunerCopy.off, style = endLabelStyle, color = colors.outline)
            Slider(
                value = gate.step.toFloat(),
                onValueChange = { value ->
                    val next = NoiseGate.ofStep(value.roundToInt())
                    if (next != currentGate) currentOnGate(next)
                },
                modifier = Modifier.weight(1f).testTag("$testTag.slider"),
                valueRange = 0f..NoiseGate.STEPS.toFloat(),
                steps = NoiseGate.STEPS - 1,
                onValueChangeFinished = onGateSettled,
                interactionSource = source,
                thumb = { TembraSliderThumb(source, Modifier.size(spacing.touchTargetMin).testTag("$testTag.handle")) },
                track = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(TembraSliderDefaults.TrackHeight)
                            .background(colors.surfaceContainerHighest, CircleShape),
                    )
                },
            )
            Text(text = TunerCopy.gateHigh, style = endLabelStyle, color = colors.outline)
        }
    }
}
