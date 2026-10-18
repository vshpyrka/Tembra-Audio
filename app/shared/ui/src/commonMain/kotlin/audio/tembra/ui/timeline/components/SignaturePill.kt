package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.timeline.TimelineTestTags

/** "4/4" beside the tempo pill (spec FR-039): read-only until User Story 5 wires [onClick] to the signature menu. */
@Composable
internal fun SignaturePill(signature: TimeSignature, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    RulerPill(
        text = signature.label,
        description = "Time signature ${signature.label}",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        testTag = TimelineTestTags.SIGNATURE_PILL,
    )
}
