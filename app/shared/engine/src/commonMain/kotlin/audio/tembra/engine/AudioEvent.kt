package audio.tembra.engine

/** Platform audio events the ViewModel reacts to (research R7). */
sealed interface AudioEvent {
    /** A call or another app took the audio: stop listening or the tone. */
    data object Interrupted : AudioEvent

    /** The interruption ended and the system says we may resume. */
    data object InterruptionEnded : AudioEvent

    /** The platform closed the stream on its own; the policy re-opens it. */
    data object DeviceLost : AudioEvent

    /** The output route (headphones) disappeared while a tone played. */
    data object OutputRouteLost : AudioEvent
}
