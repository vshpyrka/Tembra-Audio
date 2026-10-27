package audio.tembra.core.project

/** The side effect the session performs after a policy step; [None] when the step has none. */
sealed interface PlaybackTransition {
    /** `keepAlive()` then `play()`. */
    data object Start : PlaybackTransition

    /** The engine confirmed the start; nothing more to do. */
    data object BecomePlaying : PlaybackTransition

    /** The start failed: show "Could not start playback". */
    data object StopWithMessage : PlaybackTransition

    /** `pause()` then `release()`; the position is kept. */
    data object Pause : PlaybackTransition

    /** `seek(beats)`; the view follows the playhead again. */
    data class Seek(val beats: Beats) : PlaybackTransition

    data object None : PlaybackTransition
}
