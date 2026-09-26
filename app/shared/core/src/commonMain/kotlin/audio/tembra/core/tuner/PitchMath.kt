package audio.tembra.core.tuner

import kotlin.math.ceil
import kotlin.math.log2

/** Frequency to note mapping and the tuner's musical constants (contracts/tuner-domain.md). */
object PitchMath {
    const val A4_HZ = 440.0
    const val IN_TUNE_CENTS = 3.0
    const val METER_RANGE_CENTS = 50.0
    const val MIN_HZ = 50.0
    const val MAX_HZ = 1400.0

    /** Guards the halfway rule against the last bit of floating-point error (about a ten-thousandth of a cent). */
    private const val MIDPOINT_EPSILON = 1e-6

    /** Fractional MIDI number: 69 at A4, one unit per semitone. */
    fun midiFromHz(hz: Double, a4Hz: Double = A4_HZ): Double = 69.0 + 12.0 * log2(hz / a4Hz)

    /**
     * The nearest semitone and the offset to it. `n = ceil(midi − 0.5)` keeps cents in (−50, +50]: an exact midpoint
     * names the lower note at +50 (spec FR-009).
     */
    fun nearest(hz: Double, a4Hz: Double = A4_HZ): NoteReading {
        require(hz > 0.0) { "Frequency must be positive: $hz" }
        val midi = midiFromHz(hz, a4Hz)
        val n = ceil(midi - 0.5 - MIDPOINT_EPSILON).toInt()
        return NoteReading(Note.fromMidi(n), 100.0 * (midi - n))
    }
}
