package audio.tembra.core.tuner

/** The fixed preset table of spec FR-003, in display order; the first entry of each group is its default. */
object TuningPresets {

    private fun preset(id: String, name: String, instrument: Instrument, strings: GuitarStringCount?, notes: String) =
        TuningPreset(id, name, instrument, strings, notes.split(" ").map(Note::parse))

    val all: List<TuningPreset> = listOf(
        preset("guitar6-standard", "Standard", Instrument.Guitar, GuitarStringCount.Six, "E2 A2 D3 G3 B3 E4"),
        preset("guitar6-drop-d", "Drop D", Instrument.Guitar, GuitarStringCount.Six, "D2 A2 D3 G3 B3 E4"),
        preset("guitar6-half-step-down", "Half-Step Down", Instrument.Guitar, GuitarStringCount.Six, "D#2 G#2 C#3 F#3 A#3 D#4"),
        preset("guitar6-d-standard", "D Standard", Instrument.Guitar, GuitarStringCount.Six, "D2 G2 C3 F3 A3 D4"),
        preset("guitar6-dadgad", "DADGAD", Instrument.Guitar, GuitarStringCount.Six, "D2 A2 D3 G3 A3 D4"),
        preset("guitar6-open-g", "Open G", Instrument.Guitar, GuitarStringCount.Six, "D2 G2 D3 G3 B3 D4"),
        preset("guitar6-open-d", "Open D", Instrument.Guitar, GuitarStringCount.Six, "D2 A2 D3 F#3 A3 D4"),
        preset("guitar7-standard", "Standard 7-String", Instrument.Guitar, GuitarStringCount.Seven, "B1 E2 A2 D3 G3 B3 E4"),
        preset("guitar7-drop-a", "Drop A", Instrument.Guitar, GuitarStringCount.Seven, "A1 E2 A2 D3 G3 B3 E4"),
        preset("cello-standard", "Standard", Instrument.Cello, null, "C2 G2 D3 A3"),
        preset("cello-bach-5", "Bach Suite No. 5", Instrument.Cello, null, "C2 G2 D3 G3"),
    )

    /** The presets of one instrument and, for the guitar, one string count; [guitarStrings] is ignored for the cello. */
    fun forInstrument(instrument: Instrument, guitarStrings: GuitarStringCount?): List<TuningPreset> {
        val strings = if (instrument.hasStringCount) guitarStrings ?: GuitarStringCount.Six else null
        return all.filter { it.instrument == instrument && it.guitarStrings == strings }
    }

    fun default(instrument: Instrument, guitarStrings: GuitarStringCount?): TuningPreset = forInstrument(instrument, guitarStrings).first()

    fun byId(id: String): TuningPreset? = all.firstOrNull { it.id == id }
}
