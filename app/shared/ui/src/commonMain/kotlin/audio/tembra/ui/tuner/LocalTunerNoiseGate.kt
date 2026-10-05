package audio.tembra.ui.tuner

import androidx.compose.runtime.staticCompositionLocalOf
import audio.tembra.core.tuner.NoiseGate

/**
 * The debug noise gate handed down from the platform shell for this launch (feature 006, data-model.md §8); null in
 * production and without an argument.
 */
val LocalTunerNoiseGate = staticCompositionLocalOf<NoiseGate?> { null }
