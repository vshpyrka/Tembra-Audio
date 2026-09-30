package audio.tembra.ui.metronome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme

/**
 * The dial's centre (research R8): "120" in `tempoNumber` with "BPM" in `secondary`, the marking in `tertiary` below.
 * The number steps its size down to fit its width and the marking wraps to a second line, so a small landscape dial
 * or the largest font size never clips them.
 */
@Composable
internal fun TempoReadout(tempo: Tempo, marking: TempoMarking, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val typography = TembraTheme.typography
    val spacing = TembraTheme.spacing
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xs / 2)) {
        Row {
            // The number shrinks to the room "BPM" leaves (a small dial, the largest font sizes); it never clips.
            val number = TembraTheme.typographyExtras.tempoNumber
            Text(
                text = tempo.bpm.toString(),
                style = number,
                color = colors.onSurface,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = number.fontSize),
                modifier = Modifier.alignByBaseline().weight(1f, fill = false),
            )
            Text(
                text = MetronomeCopy.bpm,
                style = typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondary,
                maxLines = 1,
                modifier = Modifier.alignByBaseline().padding(start = spacing.xs),
            )
        }
        Text(text = marking.display, style = typography.labelMedium, color = colors.tertiary, textAlign = TextAlign.Center, maxLines = 2)
    }
}
