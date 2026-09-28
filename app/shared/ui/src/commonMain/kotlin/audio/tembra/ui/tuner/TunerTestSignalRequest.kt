package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Note
import audio.tembra.engine.TestSignal
import audio.tembra.engine.TestSignals

/** Parses the debug launch argument: a catalogue id or `NOTE:CENTS` such as `E2:-20` (data-model.md §9). */
object TunerTestSignalRequest {
    private val NOTE_CENTS = Regex("""([A-G]#?-?\d+):([+-]?\d+(?:\.\d+)?)""")

    fun parse(argument: String?): TestSignal? {
        if (argument.isNullOrBlank()) return null
        TestSignals.byId(argument)?.let { return it.signal }
        val match = NOTE_CENTS.matchEntire(argument) ?: return null
        val (name, cents) = match.destructured
        val note = Note.parseOrNull(name) ?: return null
        return TestSignal.Pitched(note, cents.toDouble())
    }
}
