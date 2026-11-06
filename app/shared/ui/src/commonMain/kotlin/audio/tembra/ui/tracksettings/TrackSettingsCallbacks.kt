package audio.tembra.ui.tracksettings

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Pan

/** The form's intents as plain functions (Constitution II); [None] for previews and tests. */
data class TrackSettingsCallbacks(
    val onName: (String) -> Unit,
    val onNameCommit: () -> Unit,
    val onLabel: (InstrumentLabel?) -> Unit,
    val onGain: (GainDb) -> Unit,
    val onMute: (Boolean) -> Unit,
    val onSolo: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    // Feature 012 (spec FR-007): the pan slider's steps and its release, one undo step (FR-005).
    val onPan: (Pan) -> Unit = {},
    val onPanSettled: () -> Unit = {},
) {
    companion object {
        val None = TrackSettingsCallbacks(
            onName = {}, onNameCommit = {}, onLabel = {}, onGain = {}, onMute = {}, onSolo = {},
            onDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
        )
    }
}
