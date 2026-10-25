package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.ui.metronome.components.TimeSignatureMenu
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineSheet
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState

/** The ruler's left pills (spec FR-035): the tempo pill opens the dialog, the signature pill is the metronome's signature menu. */
@Composable
internal fun RulerPills(state: TimelineUiState, callbacks: TimelineCallbacks, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        TempoPill(tempo = state.tempo, onClick = { callbacks.onOpenSheet(TimelineSheet.TempoDialog) }, enabled = state.loaded)
        TimeSignatureMenu(selected = state.timeSignature, onSelect = callbacks.onTimeSignature, testTag = TimelineTestTags.SIGNATURE_PILL)
    }
}
