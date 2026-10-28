package audio.tembra.ui.perf

import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.perf.AudioHealth

/**
 * The `engine-health.json` the iOS collector pulls at the end of the stress journey
 * (contracts/engine-audio-health.md; the transport's three counters appended by feature 009, the capture's four by
 * feature 010): schema 1, the journey, whether a stream was open, thirteen counters (zeros with no stream). The
 * alignment and the monitoring delay come from [capture], the latencies in use, as `AudioHealthPublisher` derives
 * them; both read 0 while nothing is armed. Written by hand: the file is thirteen integers and the module has no
 * JSON dependency of its own.
 */
internal fun engineHealthJson(health: AudioHealth?, capture: CaptureSnapshot? = null): String {
    val h = health ?: AudioHealth(callbacks = 0, lastMicros = 0, maxMicros = 0, overBudget = 0, xruns = 0, realtime = false)
    val alignmentMs = if (capture != null && capture.armed && capture.sampleRate > 0) capture.alignmentFrames * 1000L / capture.sampleRate else 0L
    val monitorDelayMs = if (capture != null && capture.monitoring) (capture.inputLatencyNanos + capture.outputLatencyNanos) / NANOS_PER_MILLI else 0L
    return buildString {
        append("{\"schema\":1,\"journey\":\"stress\",\"stream_open\":").append(health != null)
        append(",\"health\":{\"callbacks\":").append(h.callbacks)
        append(",\"last_us\":").append(h.lastMicros)
        append(",\"max_us\":").append(h.maxMicros)
        append(",\"over_budget\":").append(h.overBudget)
        append(",\"xruns\":").append(h.xruns)
        append(",\"realtime\":").append(if (h.realtime) 1 else 0)
        append(",\"avg_us\":").append(h.avgMicros)
        append(",\"starvation\":").append(h.starvation)
        append(",\"clips_active\":").append(h.clipsActive)
        append(",\"input_overruns\":").append(h.inputOverruns)
        append(",\"record_backlog\":").append(h.recordBacklog)
        append(",\"take_alignment_ms\":").append(alignmentMs)
        append(",\"monitor_delay_ms\":").append(monitorDelayMs)
        append("}}")
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
