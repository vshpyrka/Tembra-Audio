package audio.tembra.engine

import kotlinx.coroutines.flow.Flow

/**
 * The seam the tuner feature programs against (research R2, contracts/tuner-audio-kotlin.md). Every call runs off
 * the audio thread; the engine enforces that listening and a tone never overlap (spec FR-006).
 */
interface TunerAudio {
    fun startListening(config: TunerAnalysisConfig = TunerAnalysisConfig()): AudioResult

    /** Debug tooling (research R16): a generated tone in place of the microphone; same rules and state as [startListening]. */
    fun startTestSignal(signal: TestSignal, config: TunerAnalysisConfig = TunerAnalysisConfig()): AudioResult

    fun stopListening()

    /**
     * Lock-free; safe to call every frame. Fills [into] without allocating and is also the watchdog heartbeat. Call
     * it from the main thread only: the facades reuse one scratch buffer per engine, so two threads reading at once
     * would tear a snapshot.
     */
    fun readPitch(into: PitchSnapshot)

    /** Sine at [frequencyHz]; [durationMs] 0 loops until [stopTone]. The same frequency re-arms without a restart. */
    fun startTone(frequencyHz: Double, durationMs: Int): AudioResult

    /** Decodes synchronously; callers use `withContext(Dispatchers.Default)`. Null when the bytes are not a WAV. */
    fun loadSample(wavBytes: ByteArray): SampleHandle?

    fun playSample(handle: SampleHandle, loop: Boolean): AudioResult

    fun unloadSample(handle: SampleHandle)

    fun stopTone()

    /** Idle, Listening or Tone, read from the engine. */
    val audioState: AudioState

    /**
     * Platform interruptions and route changes (research R7). One collector is expected (the tuner ViewModel) for as
     * long as audio can be running. The flow has no replay and no `.value`: an event emitted while nothing collects
     * is dropped, which is safe because a cleared ViewModel has already stopped every stream. A feature that must
     * see every interruption should move this to a buffered `Channel` exposed as `receiveAsFlow()`.
     */
    val events: Flow<AudioEvent>
}
