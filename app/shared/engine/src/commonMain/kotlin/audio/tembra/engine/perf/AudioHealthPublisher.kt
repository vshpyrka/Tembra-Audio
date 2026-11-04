package audio.tembra.engine.perf

import audio.tembra.engine.AudioEngine
import audio.tembra.engine.CaptureSnapshot

/**
 * Publishes the engine's [AudioHealth] as the thirteen `PerfCounter` values through the facade (spec FR-015 of
 * feature 007; the transport's three since feature 009, the capture's four since feature 010,
 * contracts/perf-instrumentation.md). A plain function with no coroutine of its own: the app's telemetry ticker
 * calls it once per second in measurement builds, and a round's trace (Android) or end-of-journey readout (iOS)
 * turns the counters into the `audio.*` series. The alignment and the monitoring delay come from the capture
 * snapshot (the latencies in use); both read 0 while nothing is armed. Returns false when no stream is open.
 */
object AudioHealthPublisher {
    private const val NANOS_PER_MILLI = 1_000_000.0

    fun publish(engine: AudioEngine, trace: PerfTrace): Boolean {
        val health = engine.audioHealth() ?: return false
        val capture = CaptureSnapshot()
        engine.readCapture(capture)
        val alignmentMs = if (capture.armed && capture.sampleRate > 0) capture.alignmentFrames * 1000L / capture.sampleRate else 0L
        val monitorDelayMs = if (capture.monitoring) ((capture.inputLatencyNanos + capture.outputLatencyNanos) / NANOS_PER_MILLI).toLong() else 0L
        trace.counter(PerfCounter.AUDIO_CALLBACKS, health.callbacks)
        trace.counter(PerfCounter.AUDIO_LAST_US, health.lastMicros.toLong())
        trace.counter(PerfCounter.AUDIO_MAX_US, health.maxMicros.toLong())
        trace.counter(PerfCounter.AUDIO_OVER_BUDGET, health.overBudget.toLong())
        trace.counter(PerfCounter.AUDIO_XRUNS, health.xruns.toLong())
        trace.counter(PerfCounter.AUDIO_REALTIME, if (health.realtime) 1L else 0L)
        trace.counter(PerfCounter.AUDIO_AVG_US, health.avgMicros.toLong())
        trace.counter(PerfCounter.AUDIO_STARVATION, health.starvation.toLong())
        trace.counter(PerfCounter.AUDIO_CLIPS_ACTIVE, health.clipsActive.toLong())
        trace.counter(PerfCounter.AUDIO_INPUT_OVERRUNS, health.inputOverruns.toLong())
        trace.counter(PerfCounter.AUDIO_RECORD_BACKLOG, health.recordBacklog.toLong())
        trace.counter(PerfCounter.AUDIO_TAKE_ALIGNMENT_MS, alignmentMs)
        trace.counter(PerfCounter.AUDIO_MONITOR_DELAY_MS, monitorDelayMs)
        return true
    }
}
