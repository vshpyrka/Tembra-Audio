package audio.tembra.ui.metronome

/** What the ViewModel does for one [PlaybackEvent] (data-model.md §7). */
sealed interface PlaybackTransition {
    /** Acquire the background host, then start the engine. */
    data object Start : PlaybackTransition

    /** Stop the engine, release the host and show [message] when there is one. */
    data class Stop(val message: MetronomeMessage?) : PlaybackTransition

    data object Ignore : PlaybackTransition
}
