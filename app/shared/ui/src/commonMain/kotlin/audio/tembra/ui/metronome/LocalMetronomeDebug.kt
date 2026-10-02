package audio.tembra.ui.metronome

import androidx.compose.runtime.staticCompositionLocalOf

/** The debug request handed down from the platform shell (research R14); null in release builds and without an argument. */
val LocalMetronomeDebug = staticCompositionLocalOf<MetronomeDebugRequest?> { null }
