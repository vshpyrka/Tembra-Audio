package audio.tembra.core.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Note naming, MIDI numbering and equal-temperament frequencies (contracts/tuner-domain.md, spec FR-009). */
class NoteTest {

    @Test
    fun midiNumbersFollowTheC4Equals60Convention() {
        assertEquals(40, Note(PitchClass.E, 2).midi)
        assertEquals(69, Note(PitchClass.A, 4).midi)
        assertEquals(Note(PitchClass.A, 4), Note.fromMidi(69))
        assertEquals(Note(PitchClass.E, 2), Note.fromMidi(40))
    }

    @Test
    fun frequenciesUseA4At440Hz() {
        assertEquals(440.0, Note(PitchClass.A, 4).frequencyHz())
        assertTrue(abs(Note(PitchClass.E, 2).frequencyHz() - 82.41) < 0.01)
        assertTrue(abs(Note(PitchClass.C, 2).frequencyHz() - 65.41) < 0.01)
    }

    @Test
    fun namesAreSpelledWithSharps() {
        assertEquals("F#3", Note(PitchClass.FSharp, 3).name)
        assertEquals("Fsharp3", Note(PitchClass.FSharp, 3).resourceName)
        assertEquals("E2", Note(PitchClass.E, 2).name)
        assertEquals("E2", Note(PitchClass.E, 2).resourceName)
        assertEquals("A1", Note.fromMidi(33).name)
        assertEquals("E5", Note.fromMidi(76).name)
    }

    @Test
    fun parseAcceptsSharpSpellingOnly() {
        assertEquals(Note(PitchClass.FSharp, 3), Note.parse("F#3"))
        assertEquals(Note(PitchClass.A, 1), Note.parse("A1"))
        assertEquals(Note(PitchClass.B, 1), Note.parse("B1"))
        assertFailsWith<IllegalArgumentException> { Note.parse("Eb2") }
        assertFailsWith<IllegalArgumentException> { Note.parse("H2") }
        assertFailsWith<IllegalArgumentException> { Note.parse("E") }
        assertFailsWith<IllegalArgumentException> { Note.parse("") }
    }

    @Test
    fun parseOrNullRejectsWithoutThrowing() {
        assertEquals(Note(PitchClass.FSharp, 3), Note.parseOrNull("F#3"))
        assertEquals(Note(PitchClass.E, 2), Note.parseOrNull("E2"))
        assertNull(Note.parseOrNull("Eb2"))
        assertNull(Note.parseOrNull("E"))
        assertNull(Note.parseOrNull(""))
    }
}
