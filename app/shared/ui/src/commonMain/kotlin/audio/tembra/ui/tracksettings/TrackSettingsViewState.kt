package audio.tembra.ui.tracksettings

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId

/** What the track-settings ViewModel owns beside the session: the track shown, the name draft, the delete prompt and the 10 Hz position mirror. */
internal data class TrackSettingsViewState(
    val trackId: TrackId? = null,
    val nameDraft: String? = null,
    val confirmDelete: Boolean = false,
    val positionBeats: Beats = Beats.Zero,
    val positionSeconds: Double = 0.0,
)
