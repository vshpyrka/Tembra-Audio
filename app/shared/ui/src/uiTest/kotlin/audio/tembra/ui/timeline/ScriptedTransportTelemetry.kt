package audio.tembra.ui.timeline

import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.session.TransportTelemetry

/**
 * A [TransportTelemetry] fed by a script: [snapshotAt] maps the frame time to the snapshot the engine would publish,
 * so the readout, the ruler chip and the playhead can be driven frame by frame under a frozen clock
 * (`compose-ui-testing-patterns`). Every read is counted and the last frame time kept.
 */
class ScriptedTransportTelemetry(private val snapshotAt: (frameNanos: Long) -> TransportSnapshot) : TransportTelemetry {
    var reads: Int = 0
        private set
    var lastFrameNanos: Long = Long.MIN_VALUE
        private set

    override fun read(frameNanos: Long, into: TransportSnapshot) {
        reads++
        lastFrameNanos = frameNanos
        into.copyFrom(snapshotAt(frameNanos))
    }

    companion object {
        /** Playing from [startSeconds] at real time: one second of audio per second of frame time. */
        fun playing(startSeconds: Double, beatsPerSecond: Double = 124.0 / 60.0): ScriptedTransportTelemetry =
            ScriptedTransportTelemetry { frameNanos ->
                val seconds = startSeconds + frameNanos / 1e9
                TransportSnapshot().apply {
                    playing = true
                    audiblePositionSeconds = seconds
                    positionBeats = seconds * beatsPerSecond
                    sampleRate = 48000
                }
            }

        /** Stopped at [seconds]. */
        fun stopped(seconds: Double, beatsPerSecond: Double = 124.0 / 60.0): ScriptedTransportTelemetry =
            ScriptedTransportTelemetry {
                TransportSnapshot().apply {
                    playing = false
                    audiblePositionSeconds = seconds
                    positionBeats = seconds * beatsPerSecond
                    sampleRate = 48000
                }
            }
    }
}
