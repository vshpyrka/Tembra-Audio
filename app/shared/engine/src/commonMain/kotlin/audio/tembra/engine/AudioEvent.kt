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

    // Feature 010 (research R12): the actuals report route changes from the platform's device callbacks.
    /** The input device changed (a wired headset microphone or an interface plugged in or removed): a take ends. */
    data object InputRouteChanged : AudioEvent

    /** The output route changed; [isSpeaker] when it is now the built-in speaker (monitoring must stop). */
    data class OutputRouteChanged(val isSpeaker: Boolean) : AudioEvent
}
