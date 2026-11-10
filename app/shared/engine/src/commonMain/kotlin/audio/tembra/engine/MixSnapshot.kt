package audio.tembra.engine

import kotlin.math.log10

/**
 * Mutable mirror of `tembra_mix_snapshot` (feature 012, contracts/engine-c-abi-v8.md; data-model.md §2), one per
 * reader, filled by [TimelineAudio.readMix] without allocation from the engine's seqlock. Read in draw lambdas (the
 * strips' meters and VU dials) and by the sheet's 10 Hz mirror; never through `StateFlow`. Every level is the
 * post-fader output of a strip, or the master after its gain and clamp; zeros when metering is off or the transport is
 * closed.
 */
class MixSnapshot {
    /** Tracks of the arrangement; the entries above it are zero. */
    var trackCount: Int = 0

    /** max |x| over the last 64 ms, per track and side: index `track * 2 + side`. */
    val peak: FloatArray = FloatArray(TRACKS * 2)

    /** Root mean square over a 300 ms window, per track and side: index `track * 2 + side`. */
    val rms: FloatArray = FloatArray(TRACKS * 2)
    val masterPeak: FloatArray = FloatArray(2)
    val masterRms: FloatArray = FloatArray(2)

    /** Bit `i` while strip `i`'s clip latch is lit. */
    var clipMask: Long = 0L
    var masterClipped: Boolean = false

    /** The callback block the record was published on. */
    var block: Long = 0L
    var metering: Boolean = false

    fun clipped(track: Int): Boolean = (clipMask ushr track) and 1L == 1L

    /** The peak of [track]'s [side] (0 left, 1 right) in dBFS; [SILENCE_DB] for silence. */
    fun peakDb(track: Int, side: Int): Float = toDb(peak[track * 2 + side])

    fun rmsDb(track: Int, side: Int): Float = toDb(rms[track * 2 + side])

    fun masterPeakDb(side: Int): Float = toDb(masterPeak[side])

    fun masterRmsDb(side: Int): Float = toDb(masterRms[side])

    fun copyFrom(other: MixSnapshot) {
        trackCount = other.trackCount
        other.peak.copyInto(peak)
        other.rms.copyInto(rms)
        other.masterPeak.copyInto(masterPeak)
        other.masterRms.copyInto(masterRms)
        clipMask = other.clipMask
        masterClipped = other.masterClipped
        block = other.block
        metering = other.metering
    }

    /** Everything back to silence (the fake engine and the readers use it when metering turns off). */
    fun clear() {
        trackCount = 0
        peak.fill(0f)
        rms.fill(0f)
        masterPeak.fill(0f)
        masterRms.fill(0f)
        clipMask = 0L
        masterClipped = false
        block = 0L
        metering = false
    }

    companion object {
        /** `TEMBRA_MAX_TRACKS`. */
        const val TRACKS = 64

        /** The master's index for [TimelineAudio.resetMixClip] (`TEMBRA_MIXER_MASTER_INDEX`). */
        const val MASTER_INDEX = 64

        /** The level reported for silence, the bottom of the meters' scale. */
        const val SILENCE_DB = -120f

        fun toDb(linear: Float): Float = if (linear <= 0f) SILENCE_DB else maxOf(SILENCE_DB, 20f * log10(linear))
    }
}
