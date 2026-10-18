package audio.tembra.ui.tracksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.core.project.GainDb
import audio.tembra.ui.designsystem.TembraSlider
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The track gain as a slider over −60…+6 dB in tenths (spec FR-025) on the design system's track and handle; its
 * left end is silence (−∞). It reads "Gain, −2.4dB" and keeps Material's range semantics for `setProgress`.
 * Changes reach [onGain] while dragging, so the engine's ramp follows the finger.
 */
@Composable
internal fun GainSlider(
    gain: GainDb,
    onGain: (GainDb) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val value = if (gain == GainDb.Silence) MIN_DB else gain.value.coerceIn(MIN_DB, GainDb.MAX)
    TembraSlider(
        value = value,
        onValueChange = { raw ->
            val tenths = (raw * 10f).roundToInt() / 10f
            onGain(if (tenths <= MIN_DB) GainDb.Silence else GainDb.clamped(tenths))
        },
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics {
                contentDescription = TrackSettingsCopy.gain
                stateDescription = gain.readout
            },
        valueRange = MIN_DB..GainDb.MAX,
        interactionSource = source,
        thumb = { TembraSliderThumb(source) },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TembraSliderDefaults.TrackHeight)
                    .background(colors.surfaceContainerHighest, CircleShape),
            )
        },
    )
}

/** The slider's floor; the readout shows "−∞" there (spec FR-025). */
internal const val MIN_DB = -60f
