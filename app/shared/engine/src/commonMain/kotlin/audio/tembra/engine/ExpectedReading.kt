package audio.tembra.engine

/** What the meter must show for a catalogue test signal (data-model.md §9). */
sealed interface ExpectedReading {
    data class Note(val note: audio.tembra.core.tuner.Note, val cents: Double) : ExpectedReading

    data object Resting : ExpectedReading
}
