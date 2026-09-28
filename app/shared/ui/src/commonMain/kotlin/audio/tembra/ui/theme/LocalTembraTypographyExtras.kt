package audio.tembra.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

val LocalTembraTypographyExtras = staticCompositionLocalOf {
    TembraTypographyExtras(
        headlineLgMobile = TextStyle.Default,
        tabularTimecode = TextStyle.Default,
        tabularParam = TextStyle.Default,
        tunerNote = TextStyle.Default,
        tunerOctave = TextStyle.Default,
        tunerCardNote = TextStyle.Default,
        tunerCardOctave = TextStyle.Default,
        tunerReadout = TextStyle.Default,
        microLabel = TextStyle.Default,
    )
}
