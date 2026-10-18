package audio.tembra.core.project

/** One policy step: the next state and the side effect to perform. */
data class PlaybackOutcome(val state: PlaybackState, val transition: PlaybackTransition)
