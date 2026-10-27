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
    // Feature 010: the capture counters (contracts/engine-c-abi-v7.md health fields and the capture snapshot).
    AUDIO_INPUT_OVERRUNS,
    AUDIO_RECORD_BACKLOG,
    AUDIO_TAKE_ALIGNMENT_MS,
    AUDIO_MONITOR_DELAY_MS,
}
