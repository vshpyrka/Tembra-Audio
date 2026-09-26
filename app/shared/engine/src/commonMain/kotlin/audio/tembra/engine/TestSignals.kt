package audio.tembra.engine

import audio.tembra.core.tuner.Note

/** The fourteen developer test signals of spec FR-032, in the order the capture script walks them (data-model.md §9). */
object TestSignals {

    private fun note(id: String, name: String, cents: Double) =
        TestSignalEntry(id, TestSignal(Note.parse(name), cents), ExpectedReading.Note(Note.parse(name), cents))

    val catalogue: List<TestSignalEntry> = listOf(
        note("e2-in-tune", "E2", 0.0),
        note("e2-flat-20", "E2", -20.0),
        note("e2-sharp-20", "E2", 20.0),
        note("e2-flat-45", "E2", -45.0),
        note("a2-sharp-40", "A2", 40.0),
        note("d3-flat-5", "D3", -5.0),
        note("g3-in-tune", "G3", 0.0),
        note("b3-in-tune", "B3", 0.0),
        note("e4-in-tune", "E4", 0.0),
        note("c2-in-tune", "C2", 0.0),
        note("b1-in-tune", "B1", 0.0),
        note("a1-in-tune", "A1", 0.0),
        note("e5-in-tune", "E5", 0.0),
        TestSignalEntry("out-of-range-2khz", TestSignal(note = null, cents = 0.0, rawFrequencyHz = 2000.0), ExpectedReading.Resting),
    )

    fun byId(id: String): TestSignalEntry? = catalogue.firstOrNull { it.id == id }
}
