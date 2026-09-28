package audio.tembra.engine

import audio.tembra.core.tuner.Note
import kotlin.math.pow

/**
 * A generated plucked-string-like tone fed to the analyzer in place of the microphone (research R16, data-model.md §9):
 * [Pitched] names a note and an offset in cents, [Raw] a bare frequency for out-of-range signals.
 */
sealed interface TestSignal {
    val frequencyHz: Double
    val amplitude: Float
    val harmonics: Int
    val fundamentalGain: Float

    data class Pitched(
        val note: Note,
        val cents: Double,
        override val amplitude: Float = 0.5f,
        override val harmonics: Int = 5,
        override val fundamentalGain: Float = 0.25f,
    ) : TestSignal {
        override val frequencyHz: Double
            get() = note.frequencyHz() * 2.0.pow(cents / 1200.0)
    }

    data class Raw(
        override val frequencyHz: Double,
        override val amplitude: Float = 0.5f,
        override val harmonics: Int = 5,
        override val fundamentalGain: Float = 0.25f,
    ) : TestSignal
}
