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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.engine.PitchSnapshot
import audio.tembra.ui.designsystem.TembraSlider
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The noise gate control of Auto Listen (feature 006, spec FR-001, FR-002; contracts/tuner-screen.md §2): a label over
 * a Material [Slider] between the end labels "Off" and "High". The handle's place is the gate level, in the steps of
 * [NoiseGate]; the start of the track is off. It is built like the metronome's tempo slider and shares its handle
 * ([TembraSliderThumb]), here in a touch-target box in both directions (spec FR-018). [onGate] is raised for every
 * step the handle reaches and [onGateSettled] when the move has ended; dragging, a touch on the track and the arrow
 * keys all come from Material.
 *
 * The track is a live picture of the input (spec FR-011 to FR-013, research R10): while [listening] it fills from its
 * start to the input level on the handle's own scale, in `primary` while the sound is read and in `outline` while the
 * gate holds it back. The level comes from [telemetry] in the draw phase, every display frame, like the meter's
 * needle: the frame tick invalidates the drawing alone and nothing recomposes.
 *
 * To accessibility services it is one adjustable element (spec FR-019): the slider carries the label, the handle's
 * place ("Off" or a percentage of the track) and, when [soundAboveGate] is not null, whether the sound is above or
 * below the gate; Material's range and set-progress action stay, so the platform's adjust gestures move the handle.
 * The drawn label and end labels are hidden from those services, and nothing is a live region: the level changes all
 * the time and is never announced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoiseGateSlider(
    gate: NoiseGate,
    onGate: (NoiseGate) -> Unit,
    onGateSettled: () -> Unit,
    telemetry: PitchTelemetry,
    listening: Boolean,
    soundAboveGate: Boolean?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "tuner.noiseGate",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val endLabelStyle = TembraTheme.typographyExtras.tabularParam
    val reduceMotion = TembraTheme.motion.reduceMotion
    val tick = rememberFrameTick(listening)
    val snapshot = remember { PitchSnapshot() }
    val smoother = remember { LevelSmoother() }
    val emptyTrack = colors.surfaceContainerHighest
    val readFill = colors.primary
    val heldBackFill = colors.outline
    val currentGate by rememberUpdatedState(gate)
    val currentOnGate by rememberUpdatedState(onGate)
    val announced = announcedState(gate, soundAboveGate)

    Column(modifier = modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = TunerCopy.noiseGate.uppercase(),
            style = TembraTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
        // No gap between an end label and the slider: the handle's box already reaches 14 dp past each end of the
        // track, which is the distance the tempo slider keeps between its labels and its track.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = TunerCopy.off, style = endLabelStyle, color = colors.outline, modifier = Modifier.semantics { hideFromAccessibility() })
            TembraSlider(
                value = gate.step.toFloat(),
                onValueChange = { value ->
                    val next = NoiseGate.ofStep(value.roundToInt())
                    if (next != currentGate) currentOnGate(next)
                },
                modifier = Modifier.weight(1f).testTag("$testTag.slider").semantics {
                    contentDescription = TunerCopy.noiseGate
                    stateDescription = announced
                },
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
                            .background(emptyTrack, CircleShape)
                            .drawBehind {
                                if (!listening) return@drawBehind
                                // Reading the tick here redraws the fill every frame without recomposing anything.
                                val frameNanos = tick.longValue
                                telemetry.read(snapshot)
                                // The fill is information, like the needle: reduce motion only drops the smoothing.
                                val levelDb = if (reduceMotion) snapshot.inputLevelDb else smoother.advance(snapshot.inputLevelDb, frameNanos)
                                val end = size.width * NoiseGate.fractionOfLevel(levelDb)
                                if (end < 1f) return@drawBehind
                                drawRoundRect(
                                    color = if (snapshot.gateOpen) readFill else heldBackFill,
                                    size = Size(end, size.height),
                                    cornerRadius = CornerRadius(size.height / 2f),
                                )
                            },
                    )
                },
            )
            Text(text = TunerCopy.gateHigh, style = endLabelStyle, color = colors.outline, modifier = Modifier.semantics { hideFromAccessibility() })
        }
    }
}

/** The control's announced value: the handle's place, then whether the sound passes the gate when there is one to say. */
private fun announcedState(gate: NoiseGate, soundAboveGate: Boolean?): String {
    val place = if (gate.isOff) TunerCopy.off else TunerCopy.gatePercent(gate.percent)
    return when (soundAboveGate) {
        null -> place
        true -> "$place, ${TunerCopy.soundAboveGate}"
        false -> "$place, ${TunerCopy.soundBelowGate}"
    }
}
