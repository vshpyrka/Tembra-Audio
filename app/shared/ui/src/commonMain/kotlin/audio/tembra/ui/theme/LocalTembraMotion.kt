package audio.tembra.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Dynamic (not static) because the reduce-motion flag can change while the app runs. */
val LocalTembraMotion = compositionLocalOf { TembraMotion() }
