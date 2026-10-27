package audio.tembra.core.project

/** Swaps a track with its neighbour (spec FR-026); rejected at the ends of the list. */
data class MoveTrack(val id: TrackId, val direction: MoveDirection) : ProjectEdit
