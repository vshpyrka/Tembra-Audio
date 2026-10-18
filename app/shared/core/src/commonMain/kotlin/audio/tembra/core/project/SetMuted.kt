package audio.tembra.core.project

/** Mutes or unmutes a track (spec FR-024). */
data class SetMuted(val id: TrackId, val muted: Boolean) : ProjectEdit
