package audio.tembra.core.tuner

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fixed preset table of spec FR-003 and data-model.md §1. */
class TuningPresetsTest {

    private val expectedIds = listOf(
        "guitar6-standard", "guitar6-drop-d", "guitar6-half-step-down", "guitar6-d-standard", "guitar6-dadgad",
        "guitar6-open-g", "guitar6-open-d", "guitar7-standard", "guitar7-drop-a", "cello-standard", "cello-bach-5",
    )

    private val expectedSummaries = mapOf(
        "guitar6-standard" to "E2 A2 D3 G3 B3 E4",
        "guitar6-drop-d" to "D2 A2 D3 G3 B3 E4",
        "guitar6-half-step-down" to "D#2 G#2 C#3 F#3 A#3 D#4",
        "guitar6-d-standard" to "D2 G2 C3 F3 A3 D4",
        "guitar6-dadgad" to "D2 A2 D3 G3 A3 D4",
        "guitar6-open-g" to "D2 G2 D3 G3 B3 D4",
        "guitar6-open-d" to "D2 A2 D3 F#3 A3 D4",
        "guitar7-standard" to "B1 E2 A2 D3 G3 B3 E4",
        "guitar7-drop-a" to "A1 E2 A2 D3 G3 B3 E4",
        "cello-standard" to "C2 G2 D3 A3",
        "cello-bach-5" to "C2 G2 D3 G3",
    )

    private val expectedNames = mapOf(
        "guitar6-standard" to "Standard", "guitar6-drop-d" to "Drop D", "guitar6-half-step-down" to "Half-Step Down",
        "guitar6-d-standard" to "D Standard", "guitar6-dadgad" to "DADGAD", "guitar6-open-g" to "Open G",
        "guitar6-open-d" to "Open D", "guitar7-standard" to "Standard 7-String", "guitar7-drop-a" to "Drop A",
        "cello-standard" to "Standard", "cello-bach-5" to "Bach Suite No. 5",
    )

    @Test
    fun elevenPresetsWithUniqueIdsInOrder() {
        assertEquals(11, TuningPresets.all.size)
        assertEquals(expectedIds, TuningPresets.all.map { it.id })
        assertEquals(expectedIds.size, TuningPresets.all.map { it.id }.toSet().size)
    }

    @Test
    fun namesAndNotesMatchTheTable() {
        for (preset in TuningPresets.all) {
            assertEquals(expectedNames.getValue(preset.id), preset.name, preset.id)
            assertEquals(expectedSummaries.getValue(preset.id), preset.summary, preset.id)
        }
    }

    @Test
    fun presetsAreGroupedByInstrumentAndStringCount() {
        assertEquals(7, TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Six).size)
        assertEquals(2, TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Seven).size)
        assertEquals(2, TuningPresets.forInstrument(Instrument.Cello, null).size)
        assertEquals("guitar6-standard", TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Six).first().id)
    }

    @Test
    fun theFirstEntryIsTheDefault() {
        assertEquals("guitar6-standard", TuningPresets.default(Instrument.Guitar, GuitarStringCount.Six).id)
        assertEquals("guitar7-standard", TuningPresets.default(Instrument.Guitar, GuitarStringCount.Seven).id)
        assertEquals("cello-standard", TuningPresets.default(Instrument.Cello, null).id)
    }

    @Test
    fun byIdFindsPresetsAndRejectsUnknownIds() {
        assertEquals("Drop A", TuningPresets.byId("guitar7-drop-a")?.name)
        assertNull(TuningPresets.byId("nope"))
    }

    @Test
    fun everyPresetHasOneNotePerString() {
        for (preset in TuningPresets.all) {
            val strings = preset.guitarStrings?.count ?: 4
            assertEquals(strings, preset.notes.size, preset.id)
            assertEquals(strings, preset.strings.size, preset.id)
            assertEquals(if (preset.instrument == Instrument.Cello) null else preset.guitarStrings, preset.guitarStrings)
        }
    }

    @Test
    fun numeralsCountDownToOneForTheHighestString() {
        val strings = TuningPresets.byId("guitar7-standard")!!.strings
        assertEquals(listOf("VII", "VI", "V", "IV", "III", "II", "I"), strings.map { it.numeral })
        assertEquals(Note.parse("E4"), strings.last().note)
        assertEquals(Note.parse("B1"), strings.first().note)
    }

    @Test
    fun frequenciesAreTheEqualTemperamentValues() {
        val hz = TuningPresets.byId("guitar6-standard")!!.strings.map { round(it.frequencyHz * 10) / 10 }
        assertEquals(listOf(82.4, 110.0, 146.8, 196.0, 246.9, 329.6), hz)
        val cello = TuningPresets.byId("cello-standard")!!.strings.map { round(it.frequencyHz * 10) / 10 }
        assertEquals(listOf(65.4, 98.0, 146.8, 220.0), cello)
        val dropA = TuningPresets.byId("guitar7-drop-a")!!.strings.first()
        assertEquals(55.0, round(dropA.frequencyHz * 10) / 10)
    }

    @Test
    fun instrumentsDeclareWhetherTheyHaveAStringCount() {
        assertTrue(Instrument.Guitar.hasStringCount)
        assertFalse(Instrument.Cello.hasStringCount)
        assertEquals(listOf("guitar", "cello"), Instrument.entries.map { it.id })
        assertEquals(listOf("6-String", "7-String"), GuitarStringCount.entries.map { it.label })
        assertEquals(listOf("Auto Listen", "By Ear"), TunerMode.entries.map { it.label })
    }
}
