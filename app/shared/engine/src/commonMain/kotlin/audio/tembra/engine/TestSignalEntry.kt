package audio.tembra.engine

/** One catalogue row: a stable id for launch arguments and scripts, the signal and the expected reading. */
data class TestSignalEntry(val id: String, val signal: TestSignal, val expected: ExpectedReading)
