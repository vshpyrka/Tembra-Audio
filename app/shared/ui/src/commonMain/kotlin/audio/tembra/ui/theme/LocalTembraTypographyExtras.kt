package audio.tembra.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

val LocalTembraTypographyExtras = staticCompositionLocalOf {
    TembraTypographyExtras(TextStyle.Default, TextStyle.Default, TextStyle.Default)
}
