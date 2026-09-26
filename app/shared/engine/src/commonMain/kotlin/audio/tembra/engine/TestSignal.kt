package audio.tembra.engine

import audio.tembra.core.tuner.Note
import kotlin.math.pow

/**
 * A generated plucked-string-like tone fed to the analyzer in place of the microphone (research R16, data-model.md §9).
 * [rawFrequencyHz] overrides the note for out-of-range signals.
 */
data class TestSignal(
    val note: Note?,
    val cents: Double,
    val rawFrequencyHz: Double? = null,
    val amplitude: Float = 0.5f,
    val harmonics: Int = 5,
    val fundamentalGain: Float = 0.25f,
) {
    init {
        require(note != null || rawFrequencyHz != null) { "A test signal needs a note or a raw frequency" }
    }

    val frequencyHz: Double
        get() = rawFrequencyHz ?: (note!!.frequencyHz() * 2.0.pow(cents / 1200.0))
}
