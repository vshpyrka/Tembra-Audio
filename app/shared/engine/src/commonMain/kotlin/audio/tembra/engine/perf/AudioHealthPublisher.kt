package audio.tembra.engine.perf

import audio.tembra.engine.AudioEngine

/**
 * Publishes the engine's [AudioHealth] as the six `PerfCounter` values through the facade (spec FR-015). A plain
 * function with no coroutine of its own: the app's telemetry ticker calls it once per second in measurement builds,
 * and a round's trace (Android) or end-of-journey readout (iOS) turns the counters into the `audio.*` series.
 * Returns false when no stream is open (nothing published).
 */
object AudioHealthPublisher {
    fun publish(engine: AudioEngine, trace: PerfTrace): Boolean {
        val health = engine.audioHealth() ?: return false
        trace.counter(PerfCounter.AUDIO_CALLBACKS, health.callbacks)
        trace.counter(PerfCounter.AUDIO_LAST_US, health.lastMicros.toLong())
        trace.counter(PerfCounter.AUDIO_MAX_US, health.maxMicros.toLong())
        trace.counter(PerfCounter.AUDIO_OVER_BUDGET, health.overBudget.toLong())
        trace.counter(PerfCounter.AUDIO_XRUNS, health.xruns.toLong())
        trace.counter(PerfCounter.AUDIO_REALTIME, if (health.realtime) 1L else 0L)
        return true
    }
}
