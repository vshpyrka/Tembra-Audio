package audio.tembra.ui.mixer

import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.session.MixTelemetry
import kotlin.math.pow

/**
 * A [MixTelemetry] fed by a script (feature 012, contracts/mixer-ui.md "Tests"): [script] fills the snapshot the engine
 * would publish for a frame time, so the meters can be driven frame by frame under a frozen clock. Every read is
 * counted (the sheet performs exactly one per frame) and numbered as a block.
 */
class ScriptedMixTelemetry(private val script: (frameNanos: Long, into: MixSnapshot) -> Unit) : MixTelemetry {
    var reads: Int = 0
        private set
    var lastFrameNanos: Long = Long.MIN_VALUE
        private set
    private var block = 0L

    override fun read(frameNanos: Long, into: MixSnapshot) {
        reads++
        lastFrameNanos = frameNanos
        into.clear()
        script(frameNanos, into)
        into.metering = true
        into.block = ++block
    }

    companion object {
        /** A linear amplitude for a level in dBFS. */
        fun linear(db: Float): Float = 10f.pow(db / 20f)

        /** [trackCount] silent strips and a silent master. */
        fun silent(trackCount: Int = 3): ScriptedMixTelemetry = ScriptedMixTelemetry { _, into -> into.trackCount = trackCount }

        /** Strip [index] at [peakDb] and [rmsDb] on both sides, the others silent. */
        fun level(index: Int, peakDb: Float, rmsDb: Float, trackCount: Int = index + 1): ScriptedMixTelemetry = ScriptedMixTelemetry { _, into ->
            into.trackCount = trackCount
            into.peak[index * 2] = linear(peakDb)
            into.peak[index * 2 + 1] = linear(peakDb)
            into.rms[index * 2] = linear(rmsDb)
            into.rms[index * 2 + 1] = linear(rmsDb)
        }

        /** Strip [index] at full scale with its latch lit. */
        fun clipped(index: Int, trackCount: Int = index + 1): ScriptedMixTelemetry = ScriptedMixTelemetry { _, into ->
            into.trackCount = trackCount
            into.peak[index * 2] = 1f
            into.peak[index * 2 + 1] = 1f
            into.rms[index * 2] = linear(-3f)
            into.rms[index * 2 + 1] = linear(-3f)
            into.clipMask = 1L shl index
        }

        /** The master at [peakDb] and [rmsDb], its latch as [clipped]; [trackCount] silent strips. */
        fun master(peakDb: Float, rmsDb: Float, clipped: Boolean = false, trackCount: Int = 3): ScriptedMixTelemetry = ScriptedMixTelemetry { _, into ->
            into.trackCount = trackCount
            into.masterPeak[0] = linear(peakDb)
            into.masterPeak[1] = linear(peakDb)
            into.masterRms[0] = linear(rmsDb)
            into.masterRms[1] = linear(rmsDb)
            into.masterClipped = clipped
        }
    }
}
