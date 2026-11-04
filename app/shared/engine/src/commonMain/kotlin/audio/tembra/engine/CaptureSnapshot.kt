package audio.tembra.engine

import audio.tembra.core.recording.TakeStopReason

/**
 * Mutable mirror of `tembra_capture_snapshot`, one per reader, filled by [TimelineAudio.readCapture] without
 * allocation from the engine's seqlock (research R8). Read in draw lambdas (the meter, the growing clip) and by the
 * session's 10 Hz mirror; never through `StateFlow`.
 */
class CaptureSnapshot {
    var armed: Boolean = false
    var recording: Boolean = false
    var preRoll: Boolean = false
    var monitoring: Boolean = false
    var inputChannels: Int = 0
    var sampleRate: Int = 0

    /** max |x| over the last 64 ms per channel; 0 when silent. */
    val peak: FloatArray = FloatArray(2)

    /** Sticky until [TimelineAudio.resetClip]. */
    var clipped: Boolean = false

    /** Kept frames of the running (or last) take. */
    var framesCaptured: Long = 0L
    var takeStartFrames: Long = 0L
    var alignmentFrames: Long = 0L
    var inputLatencyNanos: Long = 0L
    var outputLatencyNanos: Long = 0L

    /** Set when the engine ended the take itself; [TakeStopReason.User] otherwise. */
    var stopReason: TakeStopReason = TakeStopReason.User
    var inputOverruns: Int = 0
    var backlogFrames: Int = 0

    /** The take's length in seconds so far. */
    val framesCapturedSeconds: Double get() = if (sampleRate > 0) framesCaptured.toDouble() / sampleRate else 0.0

    fun copyFrom(other: CaptureSnapshot) {
        armed = other.armed
        recording = other.recording
        preRoll = other.preRoll
        monitoring = other.monitoring
        inputChannels = other.inputChannels
        sampleRate = other.sampleRate
        peak[0] = other.peak[0]
        peak[1] = other.peak[1]
        clipped = other.clipped
        framesCaptured = other.framesCaptured
        takeStartFrames = other.takeStartFrames
        alignmentFrames = other.alignmentFrames
        inputLatencyNanos = other.inputLatencyNanos
        outputLatencyNanos = other.outputLatencyNanos
        stopReason = other.stopReason
        inputOverruns = other.inputOverruns
        backlogFrames = other.backlogFrames
    }
}
