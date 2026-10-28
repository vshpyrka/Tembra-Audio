package audio.tembra.ui.timeline

import audio.tembra.engine.CaptureSnapshot
import audio.tembra.ui.session.InputTelemetry

/**
 * An [InputTelemetry] fed by a script: [snapshotAt] maps the frame time to the snapshot the engine would publish, so the
 * growing clip and the input meter can be driven frame by frame under a frozen clock. Every read is counted and the
 * last frame time kept.
 */
class ScriptedInputTelemetry(private val snapshotAt: (frameNanos: Long) -> CaptureSnapshot) : InputTelemetry {
    var reads: Int = 0
        private set
    var lastFrameNanos: Long = Long.MIN_VALUE
        private set

    override fun read(frameNanos: Long, into: CaptureSnapshot) {
        reads++
        lastFrameNanos = frameNanos
        into.copyFrom(snapshotAt(frameNanos))
    }

    companion object {
        /** Recording: the kept frames grow by [framesPerSecondOfFrameTime] per second of frame time; both channels at [peak]. */
        fun recording(sampleRate: Int = 48000, framesPerSecondOfFrameTime: Double = 48000.0, peak: Float = 0f): ScriptedInputTelemetry =
            ScriptedInputTelemetry { frameNanos ->
                CaptureSnapshot().apply {
                    armed = true
                    recording = true
                    this.sampleRate = sampleRate
                    inputChannels = 1
                    this.peak[0] = peak
                    this.peak[1] = peak
                    framesCaptured = (frameNanos / 1e9 * framesPerSecondOfFrameTime).toLong()
                }
            }

        /** Armed and silent, nothing captured. */
        fun silent(sampleRate: Int = 48000): ScriptedInputTelemetry =
            ScriptedInputTelemetry { CaptureSnapshot().apply { armed = true; this.sampleRate = sampleRate; inputChannels = 1 } }

        /** Armed, not recording, a fixed [peakLinear] on both channels. */
        fun level(peakLinear: Float, clipped: Boolean = false): ScriptedInputTelemetry =
            ScriptedInputTelemetry {
                CaptureSnapshot().apply {
                    armed = true
                    sampleRate = 48000
                    inputChannels = 1
                    peak[0] = peakLinear
                    peak[1] = peakLinear
                    this.clipped = clipped
                }
            }
    }
}
