package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The tempo slider under the dial (research R8, R10): a Material [Slider] over 40–240 in whole numbers, restyled with
 * the design's 6 dp `surfaceContainerHighest` track and a 16 dp `primary` thumb, between the end labels "40" and
 * "240". The arrows come from Material; the page keys move the tempo by ten, as on the dial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoSlider(tempo: Tempo, onTempo: (Tempo) -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.slider") {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val labelStyle = TembraTheme.typographyExtras.tabularParam
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
            thumb = {
                Box(Modifier.size(MetronomeDefaults.SliderThumbSize).background(colors.primary, CircleShape))
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
