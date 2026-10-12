package audio.tembra.ui.perf

import audio.tembra.engine.perf.AudioHealth

/**
 * The `engine-health.json` the iOS collector pulls at the end of the stress journey
 * (contracts/engine-audio-health.md): schema 1, the journey, whether a stream was open, six counters (zeros with no
 * stream). Written by hand: the file is six integers and the module has no JSON dependency of its own.
 */
internal fun engineHealthJson(health: AudioHealth?): String {
    val h = health ?: AudioHealth(callbacks = 0, lastMicros = 0, maxMicros = 0, overBudget = 0, xruns = 0, realtime = false)
    return buildString {
        append("{\"schema\":1,\"journey\":\"stress\",\"stream_open\":").append(health != null)
        append(",\"health\":{\"callbacks\":").append(h.callbacks)
        append(",\"last_us\":").append(h.lastMicros)
        append(",\"max_us\":").append(h.maxMicros)
        append(",\"over_budget\":").append(h.overBudget)
        append(",\"xruns\":").append(h.xruns)
        append(",\"realtime\":").append(if (h.realtime) 1 else 0)
        append("}}")
    }
}
