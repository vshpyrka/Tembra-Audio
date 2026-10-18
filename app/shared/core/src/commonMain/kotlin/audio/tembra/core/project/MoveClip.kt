package audio.tembra.core.project

/**
 * A clip move on release (spec FR-033): along its own track the start is held at or after zero and stopped against
 * the neighbouring clips; onto another track the span must be free, otherwise the move is rejected.
 */
data class MoveClip(val clipId: ClipId, val toTrack: TrackId, val startBeats: Beats) : ProjectEdit
