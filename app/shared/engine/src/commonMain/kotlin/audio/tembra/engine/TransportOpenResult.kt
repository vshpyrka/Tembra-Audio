package audio.tembra.engine

/** Outcome of [TimelineAudio.openTransport]. */
sealed interface TransportOpenResult {
    data object Opened : TransportOpenResult

    /** Another stream owns the engine (the tuner, a tone or the standalone metronome): `TEMBRA_ERR_STATE`. */
    data class Busy(val state: AudioState) : TransportOpenResult

    /** The platform output could not open, or the platform refused focus: nothing is held. */
    data object DeviceFailed : TransportOpenResult
}
