package audio.tembra.engine

/**
 * The seam the metronome programs against (contracts/metronome-audio-kotlin.md). Every call runs off the audio
 * thread; the platform focus (Android) or session (iOS) is handled inside the actuals.
 */
interface MetronomeAudio : AudioStatus {
    /** Any state; a non-blocking publish that applies from the next audio block. */
    fun setGrid(grid: MetronomeGrid): AudioResult

    /** [ClickSoundId.Custom] needs a loaded custom sound. */
    fun selectSound(sound: ClickSoundId): AudioResult

    /** Decodes and prepares synchronously; callers use `withContext(Dispatchers.Default)`. Publishes on success. */
    fun loadCustomSound(path: String): CustomSoundResult

    fun unloadCustomSound()

    /**
     * Beat 1 sounds at once. Android returns [AudioResult.Error] with [AudioResult.FOCUS_DENIED] when audio focus is
     * refused, without touching the engine.
     */
    fun startMetronome(): AudioResult

    /** Idempotent; releases the platform focus or session. */
    fun stopMetronome()

    /** One click of the selected sound; Idle only. */
    fun previewClick(level: ClickLevel = ClickLevel.Click): AudioResult

    /** iOS reports the session's output latency; Android passes 0, which asks the device again. */
    fun reportOutputLatencyNanos(nanos: Long)

    /**
     * Lock-free; safe to call every frame. Fills [into] without allocating. Call it from the main thread only: the
     * facades reuse one scratch buffer per engine, so two threads reading at once would tear a snapshot.
     */
    fun readTick(into: TickSnapshot)
}
