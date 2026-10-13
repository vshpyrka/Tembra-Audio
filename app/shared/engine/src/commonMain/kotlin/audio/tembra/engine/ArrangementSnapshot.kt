package audio.tembra.engine

/**
 * The derived real-time view of a project (`tembra_arrangement`): every track's strip state and every clip, bounded by
 * the ABI's fixed-size struct ([MAX_TRACKS], [MAX_CLIPS]). Built by the session's mapper, never the authority
 * (Constitution V); the engine sorts clips by start per track.
 */
data class ArrangementSnapshot(
    val tracks: List<TrackRender>,
    val clips: List<ClipRender>,
) {
    init {
        require(tracks.size <= MAX_TRACKS) { "at most $MAX_TRACKS tracks, was ${tracks.size}" }
        require(clips.size <= MAX_CLIPS) { "at most $MAX_CLIPS clips, was ${clips.size}" }
    }

    companion object {
        const val MAX_TRACKS = 64
        const val MAX_CLIPS = 1024
        val Empty = ArrangementSnapshot(emptyList(), emptyList())
    }
}
