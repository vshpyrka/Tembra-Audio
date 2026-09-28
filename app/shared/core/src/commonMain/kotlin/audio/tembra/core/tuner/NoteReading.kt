package audio.tembra.core.tuner

import kotlin.math.abs

/** The nearest note to a heard frequency and the signed distance to it in cents (spec FR-008, FR-009). */
data class NoteReading(val note: Note, val cents: Double) {

    val isInTune: Boolean
        get() = abs(cents) <= PitchMath.IN_TUNE_CENTS

    val direction: PitchDirection
        get() = when {
            isInTune -> PitchDirection.InTune
            cents < 0 -> PitchDirection.Flat
            else -> PitchDirection.Sharp
        }

    val referenceHz: Double
        get() = note.frequencyHz()
}
