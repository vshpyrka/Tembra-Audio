package audio.tembra.ui.timeline.components

import audio.tembra.core.project.InstrumentLabel
import audio.tembra.ui.theme.TembraIcon

/**
 * The glyph an instrument label shows beside its text (contracts/design-system-additions.md "Icons"): Material Symbols
 * has none for Guitar, Bass, Drums, Percussion, Synth, Brass and Woodwind, whose chips show the text only, as the
 * tuner's selector does.
 */
internal object InstrumentGlyphs {
    fun of(label: InstrumentLabel): TembraIcon? = when (label) {
        InstrumentLabel.Vocals -> TembraIcon.Mic
        InstrumentLabel.Keys -> TembraIcon.Piano
        InstrumentLabel.Strings, InstrumentLabel.Other -> TembraIcon.MusicNote
        InstrumentLabel.Loops -> TembraIcon.GraphicEq
        InstrumentLabel.Guitar, InstrumentLabel.Bass, InstrumentLabel.Drums, InstrumentLabel.Percussion,
        InstrumentLabel.Synth, InstrumentLabel.Brass, InstrumentLabel.Woodwind,
        -> null
    }
}
