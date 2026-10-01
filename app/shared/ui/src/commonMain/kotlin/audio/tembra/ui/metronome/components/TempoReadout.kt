package audio.tembra.ui.metronome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme

/**
 * The dial's centre (research R8): "120" in `tempoNumber` with "BPM" in `secondary`, the marking in `tertiary` below.
 * The number steps its size down to fit its width. The marking stays on one line (ruling 2026-10-01): "Moderato
 * (108-120)" when it fits the readout's width, the name alone ("Moderato") when it does not (rings under about 240 dp:
 * the iPhone 17 portrait dial, phone landscape), and a name still too wide (the largest fonts) steps its size down
 * rather than wrap or clip. The choice is made from the width the readout is given, while it is measured.
 *
 * The three texts are hidden from screen readers: the dial's own node already announces "120 beats per minute,
 * Moderato" (spec FR-030), so they would only add three stops after it. They stay in the semantics tree for tests.
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
                modifier = Modifier.alignByBaseline().weight(1f, fill = false).semantics { hideFromAccessibility() },
            )
            Text(
                text = MetronomeCopy.bpm,
                style = typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondary,
                maxLines = 1,
                modifier = Modifier.alignByBaseline().padding(start = spacing.xs).semantics { hideFromAccessibility() },
            )
        }
        MarkingLine(marking)
    }
}

/** The marking on one line: the full "name (range)" when it fits the width offered, otherwise the name alone. */
@Composable
private fun MarkingLine(marking: TempoMarking) {
    val style = TembraTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        // A constraint read while measuring (BoxWithConstraints), not a size written back into state.
        val full = marking.display
        val fits = !constraints.hasBoundedWidth ||
            measurer.measure(full, style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        Text(
            text = if (fits) full else marking.label,
            style = style,
            color = TembraTheme.colors.tertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MetronomeDefaults.MarkingMinSize, maxFontSize = style.fontSize),
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
    }
}
