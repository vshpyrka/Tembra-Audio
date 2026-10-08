package audio.tembra.engine

import audio.tembra.engine.perf.AudioHealth

import audio.tembra.core.version.SemanticVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.round

/**
 * Test double for [AudioEngine]: never touches native code, records every call in order, follows the engine's state
 * rules and lets tests script the reading, the tick, a start failure, the custom-sound outcome, the watchdog and platform
 * events (contracts/tuner-audio-kotlin.md, contracts/metronome-audio-kotlin.md).
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

    /**
     * Every command in order, e.g. `startListening`, `startTone(82.41, 0)`, `playSample(1, true)`,
     * `setGrid(bpm=120, ticks=4, levels=ACCC)`, `selectSound(Rim808)`, `loadCustomSound(/x.wav)`, `startMetronome`,
     * `previewClick(Click)`, `reportOutputLatencyNanos(5000000)`, `setNoiseGate(-50.0)`, `setNoiseGate(off)`.
     */
    val calls: List<String> get() = _calls

    override var audioState: AudioState = AudioState.Idle

    /** What [readPitch] copies; tests script it. */
    var nextSnapshot: PitchSnapshot = PitchSnapshot()

    /** Heartbeats: how many times [readPitch] was called. */
    var readCount: Int = 0
        private set

    /** What [readTick] copies; tests script it. */
    var nextTick: TickSnapshot = TickSnapshot()

    /** What [loadCustomSound] returns for a path not in [customSoundResults]. */
    var customSoundResult: CustomSoundResult = CustomSoundResult.Loaded(
        CustomSoundInfo(sourceRate = 48000, sourceChannels = 1, sourceBits = 16, keptFrames = 4800, trimmedMs = 0f, keptMs = 100f),
    )

    /** Per-path answers of [loadCustomSound], so a test can refuse a staged file yet reload the previous copy. */
    val customSoundResults: MutableMap<String, CustomSoundResult> = mutableMapOf()

    /**
     * When set, the next start command (listening, test signal, tone, sample, metronome, preview) returns it, leaves
     * the state unchanged and clears itself.
     */
    var failNextStart: AudioResult.Error? = null

    var lastTestSignal: TestSignal? = null
        private set

    /** The last value given to [setNoiseGate]; null is no gate, which is also where the engine starts. */
    var noiseGateDb: Float? = null
        private set

    private var nextHandle = 1

    /** True from a successful [previewClick] until the preview ends ([endPreview], [stopMetronome], [stopTone]). */
    private var previewing = false

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

    override fun setNoiseGate(openDb: Float?): AudioResult {
        _calls += if (openDb == null) "setNoiseGate(off)" else "setNoiseGate($openDb)"
        noiseGateDb = openDb
        return AudioResult.Ok
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
        previewing = false
    }

    override fun setGrid(grid: MetronomeGrid): AudioResult {
        val levels = grid.levels.joinToString(separator = "") { level ->
            when (level) {
                ClickLevel.Accent -> "A"
                ClickLevel.Click -> "C"
                ClickLevel.Sub -> "S"
            }
        }
        _calls += "setGrid(bpm=${grid.tempo.bpm}, ticks=${grid.ticksPerBeat}, levels=$levels)"
        return AudioResult.Ok
    }

    override fun selectSound(sound: ClickSoundId): AudioResult {
        _calls += "selectSound(${sound.name})"
        return AudioResult.Ok
    }

    override fun loadCustomSound(path: String): CustomSoundResult {
        _calls += "loadCustomSound($path)"
        return customSoundResults[path] ?: customSoundResult
    }

    override fun unloadCustomSound() {
        _calls += "unloadCustomSound"
    }

    override fun startMetronome(): AudioResult = startOrFail("startMetronome", AudioState.Metronome)

    /** Also clears the Tone a preview left, so a test need not script the click's end before stopping. */
    override fun stopMetronome() {
        _calls += "stopMetronome"
        if (audioState == AudioState.Metronome || (audioState == AudioState.Tone && previewing)) audioState = AudioState.Idle
        previewing = false
    }

    override fun previewClick(level: ClickLevel): AudioResult {
        val result = startOrFail("previewClick(${level.name})", AudioState.Tone)
        if (result == AudioResult.Ok) previewing = true
        return result
    }

    /** The preview click ended on its own: the engine returns to Idle (not recorded as a call). */
    fun endPreview() {
        if (previewing && audioState == AudioState.Tone) audioState = AudioState.Idle
        previewing = false
    }

    override fun reportOutputLatencyNanos(nanos: Long) {
        _calls += "reportOutputLatencyNanos($nanos)"
    }

    private var scriptedHealth: AudioHealth? = null

    /** Scripts what [audioHealth] returns (SC-010: the plumbing is proven without the real engine). */
    fun scriptAudioHealth(health: AudioHealth?) {
        scriptedHealth = health
    }

    override fun audioHealth(): AudioHealth? = scriptedHealth

    override fun readTick(into: TickSnapshot) {
        into.copyFrom(nextTick)
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
