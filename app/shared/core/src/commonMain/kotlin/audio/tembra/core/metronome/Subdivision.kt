package audio.tembra.core.metronome

/** Ticks sounded inside each beat, in display order (spec FR-007). */
enum class Subdivision(val id: String, val ticksPerBeat: Int) {
    One("1", 1),
    Two("2", 2),
    Four("4", 4),
    Triplet("3", 3);

    /** The chip label; it follows the beat, so an eighth-note signature reads 1/8, 1/16, 1/32, 3let. */
    fun label(unit: BeatUnit): String = when (this) {
        One -> when (unit) {
            BeatUnit.Quarter -> "1/4"
            BeatUnit.Eighth -> "1/8"
        }
        Two -> when (unit) {
            BeatUnit.Quarter -> "1/8"
            BeatUnit.Eighth -> "1/16"
        }
        Four -> when (unit) {
            BeatUnit.Quarter -> "1/16"
            BeatUnit.Eighth -> "1/32"
        }
        Triplet -> "3let"
    }

    companion object {
        val Default = One

        fun byId(id: String): Subdivision? = entries.firstOrNull { it.id == id }
    }
}
