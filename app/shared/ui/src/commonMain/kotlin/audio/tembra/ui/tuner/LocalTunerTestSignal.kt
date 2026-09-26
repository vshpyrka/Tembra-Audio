package audio.tembra.ui.tuner

import androidx.compose.runtime.staticCompositionLocalOf
import audio.tembra.engine.TestSignal

/** The debug test signal handed down from the platform shell; null in production and without an argument. */
val LocalTunerTestSignal = staticCompositionLocalOf<TestSignal?> { null }
