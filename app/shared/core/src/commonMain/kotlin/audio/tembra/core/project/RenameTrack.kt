package audio.tembra.core.project

/** Sets a track's name (spec FR-020): trimmed, 1–40 characters; blank reverts to "Track N". */
data class RenameTrack(val id: TrackId, val name: String) : ProjectEdit
