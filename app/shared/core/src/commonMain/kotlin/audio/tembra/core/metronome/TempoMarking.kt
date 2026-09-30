package audio.tembra.core.metronome

/** Italian tempo names with the ranges shown to the user; a boundary belongs to the slower name (spec FR-005). */
enum class TempoMarking(val label: String, val low: Int, val high: Int) {
    Largo("Largo", 40, 60),
    Larghetto("Larghetto", 60, 66),
    Adagio("Adagio", 66, 76),
    Andante("Andante", 76, 108),
    Moderato("Moderato", 108, 120),
    Allegro("Allegro", 120, 156),
    Vivace("Vivace", 156, 176),
    Presto("Presto", 176, 200),
    Prestissimo("Prestissimo", 200, 240);

    val display: String get() = "$label ($low-$high)"

    companion object {
        /** The first marking whose upper bound is not below [tempo]: 120 is Moderato, 121 is Allegro. */
        fun of(tempo: Tempo): TempoMarking = entries.first { tempo.bpm <= it.high }
    }
}
