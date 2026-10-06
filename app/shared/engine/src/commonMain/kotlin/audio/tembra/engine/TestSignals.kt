package audio.tembra.engine

import audio.tembra.core.tuner.Note

/**
 * The developer test signals: the fourteen of feature 003's spec FR-032, in the order its capture script walks them
 * (data-model.md §9), then the four in-tune E2 signals at set levels that verify the noise gate (feature 006, FR-022).
 * Every expected reading is the one with no gate.
 */
object TestSignals {

    private fun note(id: String, name: String, cents: Double) =
        TestSignalEntry(id, TestSignal.Pitched(Note.parse(name), cents), ExpectedReading.Note(Note.parse(name), cents))

    /** An in-tune E2 whose level, in the noise gate's measure, is [levelDb]. */
    private fun level(id: String, levelDb: Float): TestSignalEntry {
        val e2 = Note.parse("E2")
        return TestSignalEntry(id, TestSignal.Pitched(e2, 0.0, amplitude = TestSignalLevel.amplitudeFor(levelDb)), ExpectedReading.Note(e2, 0.0))
    }

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
        TestSignalEntry("out-of-range-2khz", TestSignal.Raw(2000.0), ExpectedReading.Resting),
        level("e2-70db", -70f),
        level("e2-55db", -55f),
        level("e2-45db", -45f),
        level("e2-30db", -30f),
    )

    fun byId(id: String): TestSignalEntry? = catalogue.firstOrNull { it.id == id }
}
