package audio.tembra.core.metronome

/** The supported time signatures in display order; the tempo counts the beat named by the lower number (spec FR-006). */
enum class TimeSignature(val id: String, val label: String, val beats: Int, val unit: BeatUnit) {
    FourFour("4-4", "4/4", 4, BeatUnit.Quarter),
    ThreeFour("3-4", "3/4", 3, BeatUnit.Quarter),
    SixEight("6-8", "6/8", 6, BeatUnit.Eighth),
    TwoFour("2-4", "2/4", 2, BeatUnit.Quarter),
    FiveFour("5-4", "5/4", 5, BeatUnit.Quarter),
    SevenEight("7-8", "7/8", 7, BeatUnit.Eighth);

    companion object {
        val Default = FourFour

        fun byId(id: String): TimeSignature? = entries.firstOrNull { it.id == id }
    }
}
