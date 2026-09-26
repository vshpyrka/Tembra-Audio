package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/** Text styles without a Material slot; the tuner styles are screen-derived tokens (feature 003, research R9). */
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
)
