package audio.tembra.ui.tracksettings

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.transport.TransportUiState

/**
 * What the track-settings form renders (spec FR-041, FR-042): the track's fields from the document, the name as it is
 * being typed ([nameDraft], committed on focus loss), the pending delete confirmation, and [closed] once the track no
 * longer exists so the nested screen pops. The nested screen also carries the [transport] bar.
 */
@Immutable
data class TrackSettingsUiState(
    val trackId: TrackId? = null,
    val name: String = "",
    val label: InstrumentLabel? = null,
    val colorIndex: Int = 0,
    val gainDb: GainDb = GainDb.Unity,
    /** Feature 012 (spec FR-007): the pan the form's slider edits. */
    val pan: Pan = Pan.Centre,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val nameDraft: String? = null,
    val confirmDelete: Boolean = false,
    val closed: Boolean = false,
    val transport: TransportUiState = TransportUiState(),
) {
    /** What the name field shows: the draft while typing, the document's name otherwise. */
    val nameField: String get() = nameDraft ?: name
    val labelText: String get() = label?.displayName ?: TrackSettingsCopy.noLabel
}
