package audio.tembra.ui.tracksettings

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel

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
) {
    companion object {
        val None = TrackSettingsCallbacks(
            onName = {}, onNameCommit = {}, onLabel = {}, onGain = {}, onMute = {}, onSolo = {},
            onDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
        )
    }
}
