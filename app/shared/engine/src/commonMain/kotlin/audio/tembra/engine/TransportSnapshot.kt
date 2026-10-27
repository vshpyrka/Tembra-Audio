package audio.tembra.engine

/**
 * Mutable mirror of `tembra_transport_snapshot`, one per reader, filled by [TimelineAudio.readTransport] without
 * allocation from the engine's seqlock (research R7). [audiblePositionSeconds] already allows for the elapsed time
 * since the block and the output latency, so the playhead draws what the listener hears.
 */
class TransportSnapshot {
    var playing: Boolean = false

    /** Stream frames at the start of the last rendered block. */
    var positionFrames: Long = 0L

    /** [positionFrames] through the tempo map. */
    var positionBeats: Double = 0.0

    /** The audible position in seconds at the time of the read. */
    var audiblePositionSeconds: Double = 0.0

    /** Monotonic time of the block that produced [positionFrames]. */
    var blockTimeNanos: Long = 0L

    /** The output latency the engine subtracted. */
    var latencyNanos: Long = 0L
    var sampleRate: Int = 0

    // Feature 010 (contracts/engine-c-abi-v7.md "Appended fields").
    /** Beats of a running count-in still to sound, down to 1; -1 when no pre-roll runs. */
    var countInBeatsLeft: Int = -1

    /** True while a take captures. */
    var recording: Boolean = false

    fun copyFrom(other: TransportSnapshot) {
        playing = other.playing
        positionFrames = other.positionFrames
        positionBeats = other.positionBeats
        audiblePositionSeconds = other.audiblePositionSeconds
        blockTimeNanos = other.blockTimeNanos
        latencyNanos = other.latencyNanos
        sampleRate = other.sampleRate
        countInBeatsLeft = other.countInBeatsLeft
        recording = other.recording
    }
}
