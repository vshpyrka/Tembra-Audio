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
    // ABI revision 7 (feature 010): the capture counters; zero from a revision-6 reader.
    /** Captured frames lost since the arm (a full fifo, a short read after warm-up). */
    val inputOverruns: Int = 0,
    /** The largest count of captured frames waiting in the fifo since the take began. */
    val recordBacklog: Int = 0,
    /** The input stream's own xrun count (Android); 0 on iOS. */
    val inputXruns: Int = 0,
    // ABI revision 8 (feature 012): the mixer's counter; zero from a revision-7 reader.
    /** Blocks in which the master clamp engaged since the transport opened. */
    val masterClips: Int = 0,
)
