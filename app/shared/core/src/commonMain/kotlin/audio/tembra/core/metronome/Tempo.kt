package audio.tembra.core.metronome

import kotlin.jvm.JvmInline

/** Beats per minute, whole numbers from 40 to 240 (spec FR-002). */
@JvmInline
value class Tempo(val bpm: Int) {
    init {
        require(bpm in MIN..MAX) { "Tempo must be $MIN..$MAX bpm, was $bpm" }
    }

    /** This tempo moved by [delta] bpm, held inside the range. */
    fun plus(delta: Int): Tempo = clamped(bpm + delta)

    companion object {
        const val MIN = 40
        const val MAX = 240
        val Default = Tempo(120)

        /** [bpm] held inside the range; never throws. */
        fun clamped(bpm: Int): Tempo = Tempo(bpm.coerceIn(MIN, MAX))
    }
}
