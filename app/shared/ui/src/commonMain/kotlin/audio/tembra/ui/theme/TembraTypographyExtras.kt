package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/** Text styles without a Material slot; the tuner and metronome styles are screen-derived tokens (feature 003 R9, feature 004 R10). */
@Immutable
data class TembraTypographyExtras(
    val headlineLgMobile: TextStyle,
    val tabularTimecode: TextStyle,
    val tabularParam: TextStyle,
    val tunerNote: TextStyle,
    val tunerOctave: TextStyle,
    val tunerCardNote: TextStyle,
    val tunerCardOctave: TextStyle,
    val tunerReadout: TextStyle,
    val microLabel: TextStyle,
    val tempoNumber: TextStyle,
    val sectionLabel: TextStyle,
)
