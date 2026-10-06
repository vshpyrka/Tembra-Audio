package audio.tembra.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a level in the noise gate's own measure (RMS in dB below full scale; feature 006 research R2) into the peak
 * amplitude a [TestSignal] takes. The crest of the mix is found the way the engine's synthetic device builds its
 * tone: the partials summed in phase and probed over one cycle (data-model.md §7).
 */
object TestSignalLevel {
    /** The peak amplitude whose mix of [harmonics] partials has an RMS of [levelDb]. */
    fun amplitudeFor(levelDb: Float, harmonics: Int = 5, fundamentalGain: Float = 0.25f): Float =
        (10.0.pow(levelDb / 20.0) * crest(harmonics, fundamentalGain)).toFloat()

    /** Peak over RMS of the mix: the fundamental at [fundamentalGain], then 1, 0.5, 0.3, 0.2 and a falling tail. */
    fun crest(harmonics: Int = 5, fundamentalGain: Float = 0.25f): Double {
        val partials = maxOf(1, harmonics)
        val amplitudes = DoubleArray(partials) { k ->
            when {
                k == 0 -> fundamentalGain.toDouble()
                k - 1 < HARMONIC_LEVELS.size -> HARMONIC_LEVELS[k - 1]
                else -> HARMONIC_LEVELS.last() / (k - 3)
            }
        }
        var peak = 0.0
        for (i in 0 until PROBE_POINTS) {
            val phase = 2.0 * PI * i / PROBE_POINTS
            var sample = 0.0
            for (k in 0 until partials) sample += amplitudes[k] * sin(phase * (k + 1))
            peak = maxOf(peak, abs(sample))
        }
        val rms = sqrt(amplitudes.sumOf { it * it } / 2.0)
        return if (rms > 0.0) peak / rms else 1.0
    }

    /** Relative levels of harmonics 2 to 5, as `SyntheticAudioDevice` uses. */
    private val HARMONIC_LEVELS = doubleArrayOf(1.0, 0.5, 0.3, 0.2)
    private const val PROBE_POINTS = 4096
}
