package audio.tembra.core.tuner

import kotlin.math.pow

/**
 * A pitch class in an octave (scientific pitch notation, C4 = MIDI 60). [frequencyHz] is equal temperament around
 * A4; [resourceName] is the file-name form of [name] with `#` written `sharp` (contracts/reference-sounds.md).
 */
data class Note(val pitchClass: PitchClass, val octave: Int) {

    val midi: Int
        get() = (octave + 1) * 12 + pitchClass.ordinal

    val name: String
        get() = "${pitchClass.label}$octave"

    val resourceName: String
        get() = name.replace("#", "sharp")

    fun frequencyHz(a4Hz: Double = PitchMath.A4_HZ): Double = a4Hz * 2.0.pow((midi - 69) / 12.0)

    companion object {
        private val PATTERN = Regex("""([A-G])(#?)(-?\d+)""")

        fun fromMidi(midi: Int): Note = Note(PitchClass.entries[midi.mod(12)], midi.floorDiv(12) - 1)

        /** Parses sharp-spelled names such as `E2` or `F#3`; flats and anything else are rejected. */
        fun parse(name: String): Note {
            val match = PATTERN.matchEntire(name)
                ?: throw IllegalArgumentException("Not a sharp-spelled note name: \"$name\"")
            val (letter, sharp, octave) = match.destructured
            val label = letter + sharp
            val pitchClass = PitchClass.entries.first { it.label == label }
            return Note(pitchClass, octave.toInt())
        }
    }
}
