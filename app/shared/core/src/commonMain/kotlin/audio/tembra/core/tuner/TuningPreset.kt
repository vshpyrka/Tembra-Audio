package audio.tembra.core.tuner

/** A named, ordered tuning for one instrument and string count; [notes] run from the lowest string to the highest. */
data class TuningPreset(
    val id: String,
    val name: String,
    val instrument: Instrument,
    val guitarStrings: GuitarStringCount?,
    val notes: List<Note>,
) {
    /** Lowest string first, numerals counting down to `I` for the highest (spec FR-017). */
    val strings: List<TargetString>
        get() = notes.mapIndexed { index, note -> TargetString(RomanNumerals.of(notes.size - index), note) }

    /** `"E2 A2 D3 G3 B3 E4"`: the string list shown beside the preset name. */
    val summary: String
        get() = notes.joinToString(" ") { it.name }
}
