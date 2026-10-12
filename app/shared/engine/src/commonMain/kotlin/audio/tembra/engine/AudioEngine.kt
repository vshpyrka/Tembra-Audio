package audio.tembra.engine

import audio.tembra.engine.perf.AudioHealth

/**
 * The only way Kotlin talks to the native engine (Constitution I, II). Implemented by [NativeAudioEngine]
 * on both platforms and by [FakeAudioEngine] in tests. Every call runs on an ordinary thread.
 */
interface AudioEngine : TunerAudio, MetronomeAudio {
    fun describe(): EngineDescriptor

    /**
     * The audio thread's health counters (feature 007, spec FR-015), or null while no stream is open. A non-real-time
     * read of the atomics the callback writes; the telemetry ticker publishes it through `PerfTrace` in measurement
     * builds, never through `StateFlow`.
     */
    fun audioHealth(): AudioHealth?
}
