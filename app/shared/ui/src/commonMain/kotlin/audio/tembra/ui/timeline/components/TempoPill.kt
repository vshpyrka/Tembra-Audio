package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.timeline.TimelineTestTags

/** "124 BPM" at the ruler's left (spec FR-037): read-only until User Story 5 wires [onClick] to the tempo dialog. */
@Composable
internal fun TempoPill(tempo: Tempo, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    RulerPill(
        text = "${tempo.bpm} BPM",
        description = "Tempo ${tempo.bpm} beats per minute",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        testTag = TimelineTestTags.TEMPO_PILL,
    )
}
