package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.round

/**
 * Test double for [AudioEngine]: never touches native code, records every call in order, follows the engine's state
 * rules and lets tests script the reading, a start failure, the watchdog and platform events
 * (contracts/tuner-audio-kotlin.md).
 */
class FakeAudioEngine(
    private val descriptor: EngineDescriptor = EngineDescriptor(
        name = "Tembra Engine",
        version = SemanticVersion(0, 0, 0),
        compiledLibraries = emptyList(),
        rawDescription = "Tembra Engine 0.0.0 ()",
    ),
) : AudioEngine {
    var describeCalls: Int = 0
        private set

    private val _calls = mutableListOf<String>()

    /** Every command in order, e.g. `startListening`, `startTone(82.41, 0)`, `playSample(1, true)`. */
    val calls: List<String> get() = _calls

    override var audioState: AudioState = AudioState.Idle

    /** What [readPitch] copies; tests script it. */
    var nextSnapshot: PitchSnapshot = PitchSnapshot()

    /** Heartbeats: how many times [readPitch] was called. */
    var readCount: Int = 0
        private set

    /** When set, the next start command returns it, leaves the state unchanged and clears itself. */
    var failNextStart: AudioResult.Error? = null

    var lastTestSignal: TestSignal? = null
        private set

    private var nextHandle = 1

    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    override val events: Flow<AudioEvent> = _events

    override fun describe(): EngineDescriptor {
        describeCalls++
        return descriptor
    }

    private fun startOrFail(call: String, nextState: AudioState): AudioResult {
        _calls += call
        val failure = failNextStart
        if (failure != null) {
            failNextStart = null
            return failure
        }
        audioState = nextState
        return AudioResult.Ok
    }

    override fun startListening(config: TunerAnalysisConfig): AudioResult = startOrFail("startListening", AudioState.Listening)

    override fun startTestSignal(signal: TestSignal, config: TunerAnalysisConfig): AudioResult {
        lastTestSignal = signal
        val result = startOrFail("startTestSignal", AudioState.Listening)
        if (result == AudioResult.Ok) nextSnapshot.synthetic = true
        return result
    }

    override fun stopListening() {
        _calls += "stopListening"
        if (audioState == AudioState.Listening) audioState = AudioState.Idle
    }

    override fun readPitch(into: PitchSnapshot) {
        readCount++
        into.copyFrom(nextSnapshot)
    }

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult =
        startOrFail("startTone(${round(frequencyHz * 100) / 100}, $durationMs)", AudioState.Tone)

    override fun loadSample(wavBytes: ByteArray): SampleHandle? {
        _calls += "loadSample"
        if (wavBytes.isEmpty()) return null
        return SampleHandle(nextHandle++)
    }

    override fun playSample(handle: SampleHandle, loop: Boolean): AudioResult = startOrFail("playSample(${handle.id}, $loop)", AudioState.Tone)

    override fun unloadSample(handle: SampleHandle) {
        _calls += "unloadSample"
    }

    override fun stopTone() {
        _calls += "stopTone"
        if (audioState == AudioState.Tone) audioState = AudioState.Idle
    }

    /** Pushes a platform event into [events]. */
    fun emit(event: AudioEvent) {
        _events.tryEmit(event)
    }

    /** The engine's privacy watchdog stopped capture by itself (research R7). */
    fun simulateWatchdog() {
        audioState = AudioState.Idle
    }
}
