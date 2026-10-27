package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * "124 BPM" in the toolbar at every width (spec FR-036, D1): a [RulerPill] read as "Tempo 124 beats per minute" whose
 * tap opens the tempo dialog through [onClick]; tagged `timeline.toolbar.tempo` unless [testTag] says otherwise.
 */
@Composable
internal fun TempoPill(
    tempo: Tempo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = TimelineTestTags.TOOLBAR_TEMPO,
) {
    RulerPill(
        text = TimelineCopy.tempoPill(tempo.bpm),
        description = TimelineCopy.tempoPillDescription(tempo.bpm),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        testTag = testTag,
    )
}
