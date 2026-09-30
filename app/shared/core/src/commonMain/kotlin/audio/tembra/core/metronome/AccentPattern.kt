package audio.tembra.core.metronome

import kotlin.jvm.JvmInline

/** One [BeatLevel] per beat of the bar; the size equals the time signature's beats. */
@JvmInline
value class AccentPattern(val levels: List<BeatLevel>) {

    /** The pattern with the level of beat [beat] (0-based) advanced one step. */
    fun cycle(beat: Int): AccentPattern =
        AccentPattern(levels.mapIndexed { index, level -> if (index == beat) level.next() else level })

    /** Keeps the first [beats] levels and fills any new beats with Click (spec FR-010, no style selected). */
    fun resizedTo(beats: Int): AccentPattern =
        AccentPattern(List(beats) { index -> levels.getOrElse(index) { BeatLevel.Click } })

    /** One character per beat, for example `ACSC`. */
    fun encode(): String = levels.joinToString("") { it.id.toString() }

    companion object {
        /** Parses [text]; null when a character is not a level id, and a length other than [beats] is refitted. */
        fun decode(text: String, beats: Int): AccentPattern? {
            val levels = text.map { BeatLevel.byId(it) ?: return null }
            return AccentPattern(levels).resizedTo(beats)
        }
    }
}
