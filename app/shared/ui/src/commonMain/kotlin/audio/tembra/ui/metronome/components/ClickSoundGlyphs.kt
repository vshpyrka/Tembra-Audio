package audio.tembra.ui.metronome.components

import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.ui.theme.TembraIcon

/** The picker's glyph for each sound, as the Stitch tablet screen draws its "Click Timbre" tiles (research R10). */
internal object ClickSoundGlyphs {
    fun of(sound: BuiltInClickSound): TembraIcon = when (sound) {
        BuiltInClickSound.ClassicWood -> TembraIcon.GraphicEq
        BuiltInClickSound.Rim808 -> TembraIcon.RadioButtonChecked
        BuiltInClickSound.DigitalBeep -> TembraIcon.Notifications
        BuiltInClickSound.Cowbell -> TembraIcon.Sensors
        BuiltInClickSound.Mechanical -> TembraIcon.Tune
    }

    /** The Custom entry: "+" while nothing is imported, a check once a file is. */
    fun custom(filled: Boolean): TembraIcon = if (filled) TembraIcon.Check else TembraIcon.Add
}
