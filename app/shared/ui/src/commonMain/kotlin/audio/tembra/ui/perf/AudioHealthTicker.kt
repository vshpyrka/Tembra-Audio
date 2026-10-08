package audio.tembra.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.perf.AudioHealthPublisher
import audio.tembra.engine.perf.PerfTrace
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Publishes the engine's audio-health counters once per second through the facade while the root composition lives
 * (feature 007, spec FR-015). The named launch site of the publisher: a `LaunchedEffect` keyed by the engine, owned
 * by the root wiring composable, cancelled with it. Composed only in measurement builds; when the audio spine adds
 * the app's telemetry loop, this folds into it.
 */
@Composable
fun AudioHealthTicker(engine: AudioEngine) {
    LaunchedEffect(engine) {
        while (isActive) {
            AudioHealthPublisher.publish(engine, PerfTrace)
            delay(TICK_MILLIS)
        }
    }
}

private const val TICK_MILLIS = 1_000L
