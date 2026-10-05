package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Note
import audio.tembra.engine.TestSignal
import audio.tembra.engine.TestSignalLevel
import audio.tembra.engine.TestSignals

/**
 * Parses the debug launch argument: a catalogue id, `NOTE:CENTS` such as `E2:-20` (data-model.md §9), or
 * `NOTE:CENTS@LEVEL` such as `E2:0@-55`, the note at that level in the noise gate's own measure, from −120 to 0 dB
 * (feature 006, data-model.md §8).
 */
object TunerTestSignalRequest {
    private val NOTE_CENTS = Regex("""([A-G]#?-?\d+):([+-]?\d+(?:\.\d+)?)(?:@(-?\d+(?:\.\d+)?))?""")
    private val LEVELS = -120f..0f

    fun parse(argument: String?): TestSignal? {
        if (argument.isNullOrBlank()) return null
        TestSignals.byId(argument)?.let { return it.signal }
        val match = NOTE_CENTS.matchEntire(argument) ?: return null
        val (name, cents, level) = match.destructured
        val note = Note.parseOrNull(name) ?: return null
        if (level.isEmpty()) return TestSignal.Pitched(note, cents.toDouble())
        val levelDb = level.toFloat().takeIf { it in LEVELS } ?: return null
        return TestSignal.Pitched(note, cents.toDouble(), amplitude = TestSignalLevel.amplitudeFor(levelDb))
    }
}
