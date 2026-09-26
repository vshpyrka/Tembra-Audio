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

    /** Lock-free; safe to call every frame from the UI thread. Fills [into] without allocating. Also the watchdog heartbeat. */
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

    /** Platform interruptions and route changes (research R7). */
    val events: Flow<AudioEvent>
}
