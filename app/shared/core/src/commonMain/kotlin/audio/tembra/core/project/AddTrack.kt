package audio.tembra.core.project

/** "+ Track" (spec FR-019): appends "Track N" with the next palette colour and the defaults; [id] is the new track's identity. */
data class AddTrack(val id: TrackId = IdGenerator.trackId()) : ProjectEdit
