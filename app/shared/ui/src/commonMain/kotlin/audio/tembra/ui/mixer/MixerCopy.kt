package audio.tembra.ui.mixer

import audio.tembra.ui.designsystem.LevelMeterCopy

/**
 * The mixer's user-facing strings (feature 012, contracts/mixer-ui.md "Copy"), kept as data so tests assert the exact
 * text. The pan readouts ("C", "L 35", "R 20") are `Pan.readout`'s, owned by the core type; the gain readouts are
 * `GainDb.readout`'s; the master's state ("80 percent") and percentage ("80%") are `TransportCopy`'s, as feature 009's
 * slider reads them.
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
    const val level = LevelMeterCopy.level
    const val vu = LevelMeterCopy.vu
    const val clipped = "clipped"
    const val clipIndicator = LevelMeterCopy.clipIndicator
    const val clippedTapToReset = LevelMeterCopy.clipped
    const val openInWindow = "Open in a window"

    /** The handle's state: which height the sheet stands at. */
    const val halfHeight = "Half height"
    const val fullHeight = "Full height"

    /** A fader's accessible name, read before its readout: "Lead Guitar, gain" + "−3.5dB" (spec FR-030). */
    fun gainDescription(track: String): String = "$track, ${gain.lowercase()}"

    /** A knob's accessible name: "Lead Guitar, pan" + "L 35" (spec FR-030). */
    fun panDescription(track: String): String = "$track, ${pan.lowercase()}"

    /** A meter's level on request: "Level, −12 dB"; silence reads "Level, silent". */
    fun levelDescription(db: Int?): String = if (db == null) "$level, silent" else "$level, $db dB"
}
