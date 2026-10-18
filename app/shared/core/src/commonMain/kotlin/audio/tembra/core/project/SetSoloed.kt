package audio.tembra.core.project

/** Solos or unsolos a track (spec FR-024); several tracks may be soloed. */
data class SetSoloed(val id: TrackId, val soloed: Boolean) : ProjectEdit
