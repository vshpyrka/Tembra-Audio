package audio.tembra.ui.mixer

/**
 * The mixer's user-facing strings (feature 012, contracts/mixer-ui.md "Copy"), kept as data so tests assert the exact
 * text. The pan readouts ("C", "L 35", "R 20") are `Pan.readout`'s, owned by the core type; the gain readouts are
 * `GainDb.readout`'s; the master's percentage is `Percent.readout`'s.
 */
object MixerCopy {
    const val mixer = "Mixer"
    const val openMixer = "Open mixer"
    const val closeMixer = "Close mixer"
    const val gain = "Gain"
    const val pan = "Pan"
    const val solo = "Solo"
    const val mute = "Mute"
    const val master = "Master"
    const val masterVolume = "Master volume"
    const val level = "Level"
    const val vu = "VU"
    const val clipped = "clipped"
    const val clippedTapToReset = "Clipped, tap to reset"
    const val openInWindow = "Open in a window"

    /** A fader's accessible description: "Lead Guitar, gain, −3.5dB" (spec FR-030). */
    fun gainDescription(track: String, readout: String): String = "$track, $gain, $readout".lowercaseMiddle()

    /** A knob's accessible description: "Lead Guitar, pan, L 35" (spec FR-030). */
    fun panDescription(track: String, readout: String): String = "$track, $pan, $readout".lowercaseMiddle()

    /** The master strip's: "Master volume, 80 percent" (spec FR-030). */
    fun masterVolumeDescription(percent: Int): String = "$masterVolume, $percent percent"

    /** A meter's level on request: "Level, −12 dB"; silence reads "Level, silent". */
    fun levelDescription(db: Int?): String = if (db == null) "$level, silent" else "$level, $db dB"

    /** "<track>, Gain, x" → "<track>, gain, x": the control's name is read in lower case after the track's. */
    private fun String.lowercaseMiddle(): String {
        val first = indexOf(", ")
        if (first < 0) return this
        val second = indexOf(", ", first + 2)
        if (second < 0) return this
        return substring(0, first + 2) + substring(first + 2, second).lowercase() + substring(second)
    }
}
