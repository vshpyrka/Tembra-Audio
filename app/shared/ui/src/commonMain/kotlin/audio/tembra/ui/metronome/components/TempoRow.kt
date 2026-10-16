package audio.tembra.ui.metronome.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The dial's controls without its ring, for a Pocket screen too short for the 160 dp dial (2026-10-16): "−", the
 * readout with the marking beside it, "+", then [trailing] (the Pocket form's settings button). The readout is the same
 * slider node as the dial's (range, state, the progress action, arrow and page keys) with a focus outline, so a screen
 * reader and a keyboard meet the tempo as they do on the dial. Tagged [testTag], its steppers `$testTag.down` and `.up`.
 */
@Composable
internal fun TempoRow(
    tempo: Tempo,
    marking: TempoMarking,
    canStepDown: Boolean,
    canStepUp: Boolean,
    onTempo: (Tempo) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.tempoRow",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val currentTempo by rememberUpdatedState(tempo)
    val currentOnTempo by rememberUpdatedState(onTempo)
    val state = MetronomeCopy.dialState(tempo, marking)

    /** Sends [next] unless it is the current tempo. */
    fun offer(next: Tempo) {
        if (next != currentTempo) currentOnTempo(next)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TempoStepper(
            icon = TembraIcon.Remove,
            enabled = canStepDown,
            contentDescription = MetronomeCopy.decreaseTempo,
            onStep = { onStep(-1) },
            testTag = "$testTag.down",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
                .semantics {
                    stateDescription = state
                    progressBarRangeInfo = ProgressBarRangeInfo(tempo.bpm.toFloat(), Tempo.MIN.toFloat()..Tempo.MAX.toFloat(), steps = TEMPO_SLIDER_STEPS)
                    setProgress { value ->
                        offer(Tempo.clamped(value.roundToInt()))
                        true
                    }
                }
                .onKeyEvent { event -> handleKey(event, currentTempo)?.let(::offer) != null }
                .focusable(interactionSource = source)
                .then(if (focused) Modifier.border(spacing.focusRingWidth, colors.primary, RoundedCornerShape(TembraShapes.small)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TempoReadout(tempo = tempo, marking = marking, markingBeside = true)
        }
        TempoStepper(
            icon = TembraIcon.Add,
            enabled = canStepUp,
            contentDescription = MetronomeCopy.increaseTempo,
            onStep = { onStep(1) },
            testTag = "$testTag.up",
        )
        trailing?.invoke()
    }
}
