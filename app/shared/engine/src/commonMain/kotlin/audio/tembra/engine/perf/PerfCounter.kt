package audio.tembra.engine.perf

/** The audio-health counters the telemetry drain publishes once per second in measurement builds (spec FR-015). */
enum class PerfCounter {
    AUDIO_CALLBACKS,
    AUDIO_LAST_US,
    AUDIO_MAX_US,
    AUDIO_OVER_BUDGET,
    AUDIO_XRUNS,
    AUDIO_REALTIME,
    // Feature 009: the transport's counters (contracts/engine-c-abi-v6.md health fields).
    AUDIO_AVG_US,
    AUDIO_STARVATION,
    AUDIO_CLIPS_ACTIVE,
}
