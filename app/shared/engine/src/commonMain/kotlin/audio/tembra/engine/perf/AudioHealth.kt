package audio.tembra.engine.perf

/**
 * The engine's audio-thread health (spec FR-015, contracts/engine-audio-health.md): a copy of the counters the
 * callback writes with atomic stores, read on a host thread through the C ABI. Zeros until the audio spine fills
 * them; null from [audio.tembra.engine.AudioEngine.audioHealth] while no stream is open.
 */
data class AudioHealth(
    val callbacks: Long,
    val lastMicros: Int,
    val maxMicros: Int,
    val overBudget: Int,
    val xruns: Int,
    val realtime: Boolean,
)
