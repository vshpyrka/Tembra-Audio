package audio.tembra.core.tuner

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Frequency to nearest semitone, cents, the halfway rule and the in-tune tolerance (spec FR-008, FR-009, FR-028). */
class PitchMathTest {

    private fun assertCents(expected: Double, actual: Double, tolerance: Double = 0.01) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected cents, was $actual")

    @Test
    fun everyPresetStringMapsToItsOwnNoteWithZeroCents() {
        for (preset in TuningPresets.all) {
            for (string in preset.strings) {
                val reading = PitchMath.nearest(string.frequencyHz)
                assertEquals(string.note, reading.note, "${preset.id} ${string.numeral}")
                assertTrue(abs(reading.cents) < 0.001, "${preset.id} ${string.numeral} read ${reading.cents} cents")
                assertEquals(PitchDirection.InTune, reading.direction)
                assertTrue(reading.isInTune)
            }
        }
    }

    @Test
    fun offsetsAreMeasuredInCentsFromTheNearestNote() {
        val e2 = Note.parse("E2").frequencyHz()
        val sharp = PitchMath.nearest(e2 * 2.0.pow(20.0 / 1200))
        assertEquals(Note.parse("E2"), sharp.note)
        assertCents(20.0, sharp.cents)
        assertEquals(PitchDirection.Sharp, sharp.direction)
        assertFalse(sharp.isInTune)

        val flat = PitchMath.nearest(e2 * 2.0.pow(-20.0 / 1200))
        assertEquals(Note.parse("E2"), flat.note)
        assertCents(-20.0, flat.cents)
        assertEquals(PitchDirection.Flat, flat.direction)
        assertFalse(flat.isInTune)
    }

    @Test
    fun theExactMidpointShowsTheLowerNoteAtPlusFifty() {
        val midpoint = 440.0 * 2.0.pow((40.5 - 69) / 12)
        val reading = PitchMath.nearest(midpoint)
        assertEquals(Note.parse("E2"), reading.note)
        assertCents(50.0, reading.cents)
        assertEquals(PitchDirection.Sharp, reading.direction)

        val justAbove = 440.0 * 2.0.pow((40.51 - 69) / 12)
        val next = PitchMath.nearest(justAbove)
        assertEquals(Note.parse("F2"), next.note)
        assertCents(-49.0, next.cents)
        assertEquals(PitchDirection.Flat, next.direction)
    }

    @Test
    fun inTuneToleranceIsInclusiveAtThreeCents() {
        val e2 = Note.parse("E2")
        assertTrue(NoteReading(e2, 3.0).isInTune)
        assertTrue(NoteReading(e2, -3.0).isInTune)
        assertEquals(PitchDirection.InTune, NoteReading(e2, 3.0).direction)
        assertFalse(NoteReading(e2, 3.01).isInTune)
        assertFalse(NoteReading(e2, -3.01).isInTune)
        assertEquals(PitchDirection.Sharp, NoteReading(e2, 3.01).direction)
        assertEquals(PitchDirection.Flat, NoteReading(e2, -3.01).direction)
    }

    @Test
    fun hearingRangeEndsAreNamedCorrectly() {
        assertEquals("A1", PitchMath.nearest(55.0).note.name)
        assertEquals("E5", PitchMath.nearest(659.26).note.name)
    }

    @Test
    fun referenceFrequencyIsTheNotesOwn() {
        val reading = PitchMath.nearest(83.0)
        assertEquals(reading.note.frequencyHz(), reading.referenceHz)
    }

    @Test
    fun constantsMatchTheSpec() {
        assertEquals(440.0, PitchMath.A4_HZ)
        assertEquals(3.0, PitchMath.IN_TUNE_CENTS)
        assertEquals(50.0, PitchMath.METER_RANGE_CENTS)
        assertEquals(50.0, PitchMath.MIN_HZ)
        assertEquals(1400.0, PitchMath.MAX_HZ)
        assertEquals(69.0, PitchMath.midiFromHz(440.0))
    }
}
