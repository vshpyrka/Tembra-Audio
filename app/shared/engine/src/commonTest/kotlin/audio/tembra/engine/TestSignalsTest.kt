package audio.tembra.engine

import audio.tembra.core.tuner.Note
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The developer test-signal catalogue (data-model.md §9, spec FR-032). */
class TestSignalsTest {

    private val expectedIds = listOf(
        "e2-in-tune", "e2-flat-20", "e2-sharp-20", "e2-flat-45", "a2-sharp-40", "d3-flat-5", "g3-in-tune", "b3-in-tune",
        "e4-in-tune", "c2-in-tune", "b1-in-tune", "a1-in-tune", "e5-in-tune", "out-of-range-2khz",
    )

    @Test
    fun fourteenUniqueIdsInOrder() {
        assertEquals(14, TestSignals.catalogue.size)
        assertEquals(expectedIds, TestSignals.catalogue.map { it.id })
        assertEquals(14, TestSignals.catalogue.map { it.id }.toSet().size)
    }

    @Test
    fun offsetsAreAppliedInCents() {
        val flat = assertIs<TestSignal.Pitched>(TestSignals.byId("e2-flat-20")!!.signal)
        assertTrue(abs(flat.frequencyHz - 82.41 * 2.0.pow(-20.0 / 1200)) < 0.01)
        assertEquals(Note.parse("E2"), flat.note)
        assertEquals(-20.0, flat.cents)
        assertEquals(ExpectedReading.Note(Note.parse("E2"), -20.0), TestSignals.byId("e2-flat-20")!!.expected)
    }

    @Test
    fun theOutOfRangeEntryHasARawFrequencyAndRests() {
        val entry = TestSignals.byId("out-of-range-2khz")!!
        assertIs<TestSignal.Raw>(entry.signal)
        assertEquals(2000.0, entry.signal.frequencyHz)
        assertEquals(ExpectedReading.Resting, entry.expected)
    }

    @Test
    fun inTuneEntriesExpectTheirNote() {
        assertEquals(ExpectedReading.Note(Note.parse("A1"), 0.0), TestSignals.byId("a1-in-tune")!!.expected)
        assertEquals(ExpectedReading.Note(Note.parse("E5"), 0.0), TestSignals.byId("e5-in-tune")!!.expected)
        assertEquals(ExpectedReading.Note(Note.parse("D3"), -5.0), TestSignals.byId("d3-flat-5")!!.expected)
        assertEquals(ExpectedReading.Note(Note.parse("A2"), 40.0), TestSignals.byId("a2-sharp-40")!!.expected)
    }

    @Test
    fun defaultsDescribeAPluckedString() {
        val signal = TestSignals.byId("e2-in-tune")!!.signal
        assertEquals(0.5f, signal.amplitude)
        assertEquals(5, signal.harmonics)
        assertEquals(0.25f, signal.fundamentalGain)
        assertEquals(Note.parse("E2").frequencyHz(), signal.frequencyHz)
    }

    @Test
    fun unknownIdsAreNull() {
        assertNull(TestSignals.byId("nope"))
    }
}
