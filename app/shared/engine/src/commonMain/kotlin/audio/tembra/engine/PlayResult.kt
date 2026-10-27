package audio.tembra.engine

/** Outcome of [TimelineAudio.play]. */
sealed interface PlayResult {
    data object Started : PlayResult

    /** The engine refused ([code] is the `tembra_error`); playback did not start. */
    data class Failed(val code: Int) : PlayResult
}
