package audio.tembra.ui.shortcuts

import androidx.compose.runtime.staticCompositionLocalOf

/** The shell's [TextInputFocus], which the app's text fields report to; none outside the shell (previews, tests). */
val LocalTextInputFocus = staticCompositionLocalOf<TextInputFocus?> { null }
