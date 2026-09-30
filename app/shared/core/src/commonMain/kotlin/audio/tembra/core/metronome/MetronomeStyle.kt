package audio.tembra.core.metronome

/** A preset that fills every accent cell at once (spec FR-010). */
enum class MetronomeStyle(val id: String, val label: String) {
    Standard("standard", "Standard"),
    Downbeat("downbeat", "Downbeat"),
    Syncopated("syncopated", "Syncopated");

    /** The cells this style sets for [signature]. */
    fun pattern(signature: TimeSignature): AccentPattern {
        val levels = List(signature.beats) { index ->
            val beat = index + 1
            when (this) {
                Standard -> if (beat == 1 || (signature == TimeSignature.SixEight && beat == 4)) BeatLevel.Accent else BeatLevel.Click
                Downbeat -> if (beat == 1) BeatLevel.Accent else BeatLevel.Sub
                Syncopated -> if (beat % 2 == 0) BeatLevel.Accent else BeatLevel.Click
            }
        }
        return AccentPattern(levels)
    }

    companion object {
        /** The first style whose pattern equals [pattern], or null when the cells were edited by hand. */
        fun matching(pattern: AccentPattern, signature: TimeSignature): MetronomeStyle? =
            entries.firstOrNull { it.pattern(signature) == pattern }

        fun byId(id: String): MetronomeStyle? = entries.firstOrNull { it.id == id }
    }
}
