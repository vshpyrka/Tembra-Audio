package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.PitchDirection
import audio.tembra.core.tuner.PitchMath
import audio.tembra.engine.PitchSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** The 10 Hz sample of the engine's reading through the nearest-note rule; null means resting (data-model.md §4). */
data class MeterReading(
    val note: Note,
    val cents: Double,
    val direction: PitchDirection,
    val isInTune: Boolean,
    val frequencyHz: Double,
    val referenceHz: Double,
) {
    /** What a screen reader announces: `"E2, 4 cents sharp"`, `"F#3, in tune"`. */
    val stateDescription: String
        get() {
            val amount = abs(cents).roundToInt()
            return when {
                isInTune -> "${note.name}, ${TunerCopy.inTune.lowercase()}"
                direction == PitchDirection.Flat -> "${note.name}, $amount cents ${TunerCopy.flat.lowercase()}"
                else -> "${note.name}, $amount cents ${TunerCopy.sharp.lowercase()}"
            }
        }

    companion object {
        /** The reading of a snapshot, or null when the engine hears no clear note. */
        fun from(snapshot: PitchSnapshot): MeterReading? {
            if (!snapshot.hasPitch || snapshot.frequencyHz <= 0f) return null
            val hz = snapshot.frequencyHz.toDouble()
            val nearest = PitchMath.nearest(hz)
            return MeterReading(nearest.note, nearest.cents, nearest.direction, nearest.isInTune, hz, nearest.referenceHz)
        }

        /** The announcement for a possibly resting reading. */
        fun describe(reading: MeterReading?): String = reading?.stateDescription ?: TunerCopy.listening
    }
}
