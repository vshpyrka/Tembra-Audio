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
    // ABI revision 6 (feature 009): the transport's counters; zero from a revision-5 reader.
    /** Mean callback duration since the stream opened, microseconds. */
    val avgMicros: Int = 0,
    /** Clip fifos found dry by the callback (silence was rendered for them). */
    val starvation: Int = 0,
    /** Clips rendered in the last block. */
    val clipsActive: Int = 0,
)
