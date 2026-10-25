package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.metronome.components.TapTempoButton
import audio.tembra.ui.metronome.components.TempoDial
import audio.tembra.ui.metronome.components.TempoSlider
import audio.tembra.ui.metronome.components.TempoStepper
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy

/**
 * The project tempo (spec FR-035): the metronome's dial, steppers, slider and tap button reused under `timeline.*`
 * tags. Every change reaches [onTempoDragging] at once so the ruler, the clips and the click follow; "Done" commits
 * the run as one undo step ([onTempoCommit]). Dismissing commits as well: the project already holds the value.
 */
@Composable
internal fun TempoDialog(
    tempo: Tempo,
    tapCaption: TapCaption,
    onTempoDragging: (Tempo) -> Unit,
    onTempoCommit: (Tempo) -> Unit,
    onTapTempo: (nanos: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val commit = {
        onTempoCommit(tempo)
        onDismiss()
    }
    Dialog(onDismissRequest = commit) {
        Surface(
            modifier = modifier.anchorRoot().testTag(TEMPO_DIALOG_TAG),
            shape = RoundedCornerShape(TembraShapes.large),
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(TimelineCopy.tempo, style = TembraTheme.typography.headlineSmall, color = colors.onSurface)
                TempoDial(
                    tempo = tempo,
                    marking = TempoMarking.of(tempo),
                    canStepDown = tempo.bpm > Tempo.MIN,
                    canStepUp = tempo.bpm < Tempo.MAX,
                    onTempo = onTempoDragging,
                    onStep = { onTempoDragging(tempo.plus(it)) },
                    modifier = Modifier.size(DIAL_SIZE),
                    testTag = TEMPO_DIAL_TAG,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    TempoStepper(
                        icon = TembraIcon.Remove,
                        enabled = tempo.bpm > Tempo.MIN,
                        contentDescription = TimelineCopy.tempoDown,
                        onStep = { onTempoDragging(tempo.plus(-1)) },
                        testTag = TEMPO_DOWN_TAG,
                    )
                    TempoSlider(tempo = tempo, onTempo = onTempoDragging, modifier = Modifier.weight(1f), testTag = TEMPO_SLIDER_TAG)
                    TempoStepper(
                        icon = TembraIcon.Add,
                        enabled = tempo.bpm < Tempo.MAX,
                        contentDescription = TimelineCopy.tempoUp,
                        onStep = { onTempoDragging(tempo.plus(1)) },
                        testTag = TEMPO_UP_TAG,
                    )
                }
                TapTempoButton(
                    caption = MetronomeCopy.caption(tapCaption),
                    onTap = onTapTempo,
                    modifier = Modifier.fillMaxWidth(),
                    stateDescription = MetronomeCopy.tapState(tapCaption, tempo),
                    testTag = TEMPO_TAP_TAG,
                )
                TextButton(onClick = commit, modifier = Modifier.align(Alignment.End).testTag(TEMPO_DONE_TAG)) {
                    Text(TimelineCopy.done, color = colors.primary, style = TembraTheme.typography.labelLarge)
                }
            }
        }
    }
}

internal const val TEMPO_DIALOG_TAG = "dialog.tempo"
private val DIAL_SIZE = 200.dp
internal const val TEMPO_DIAL_TAG = "timeline.tempo.dial"
internal const val TEMPO_SLIDER_TAG = "timeline.tempo.slider"
internal const val TEMPO_TAP_TAG = "timeline.tempo.tap"
internal const val TEMPO_DOWN_TAG = "timeline.tempo.down"
internal const val TEMPO_UP_TAG = "timeline.tempo.up"
internal const val TEMPO_DONE_TAG = "timeline.tempo.done"
