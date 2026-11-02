package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.version.SemanticVersion
import audio.tembra.engine.perf.AudioHealth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.round

/**
 * Test double for [AudioEngine]: never touches native code, records every call in order, follows the engine's state
 * rules and lets tests script the reading, the tick, a start failure, the custom-sound outcome, the watchdog and platform
 * events (contracts/tuner-audio-kotlin.md, contracts/metronome-audio-kotlin.md). The transport half
 * (contracts/timeline-audio-kotlin.md) records its calls too and scripts the inspector, clip-source refusals, a play
 * failure, the transport snapshot and the overview writer. The capture half (feature 010, contracts/engine-c-abi-v7.md
 * "Kotlin seam") scripts the arm result, the capture snapshot, the peak buckets, the take info and a take writer that
 * puts a tiny valid WAV where the engine would, so the adoption path is testable on the JVM.
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

    // --- Transport (feature 009, contracts/timeline-audio-kotlin.md) -------------------------------------------------

    /** What [inspect] answers per path; a path not listed answers [defaultInspectResult]. */
    val inspectResults: MutableMap<String, AudioInfo?> = mutableMapOf()

    /** The answer for a path [inspectResults] does not name: a 48 kHz stereo WAV of one second by default. */
    var defaultInspectResult: AudioInfo? = AudioInfo(format = audio.tembra.core.project.AudioFormat.Wav, channels = 2, sampleRate = 48000, frames = 48000)

    /** Paths whose [openClipSource] answers [ClipSourceResult.Undecodable]. */
    val clipSourceFailures: MutableSet<String> = mutableSetOf()

    /** When set, the next [play] returns it, leaves the transport stopped and clears itself. */
    var failNextPlay: PlayResult.Failed? = null

    /** What [readTransport] copies; tests script it. */
    var nextTransport: TransportSnapshot = TransportSnapshot()

    /** How many times [readTransport] was called. */
    var readTransportCount: Int = 0
        private set

    /** Called by [buildOverview] with the overview path so a test can write a tiny valid `.peaks`; null writes nothing and succeeds. */
    var overviewWriter: ((overviewPath: String) -> Boolean)? = null

    /** The open clip sources by handle id: path and the info they were opened with. */
    val openClipSources: MutableMap<Int, Pair<String, AudioInfo>> = mutableMapOf()

    /** The last arrangement [setArrangement] received. */
    var lastArrangement: ArrangementSnapshot? = null
        private set

    /** True between [play] and [pause]; [nextTransport] is what the readers see. */
    var transportPlaying: Boolean = false
        private set

    private var nextClipHandle = 1

    /** When set, [openTransport] suspends on it before changing state: a test can leave while the device opens. */
    var openTransportGate: CompletableDeferred<Unit>? = null

    /** How many of the next [openTransport] calls answer `Busy(Listening)`, as a tuner still letting go of the device would. */
    var transportBusyFor: Int = 0

    override suspend fun openTransport(): TransportOpenResult {
        _calls += "openTransport"
        openTransportGate?.await()
        val failure = failNextStart
        if (failure != null) {
            failNextStart = null
            return TransportOpenResult.DeviceFailed
        }
        if (transportBusyFor > 0) {
            transportBusyFor--
            return TransportOpenResult.Busy(AudioState.Listening)
        }
        return when (audioState) {
            AudioState.Idle -> {
                audioState = AudioState.Transport
                TransportOpenResult.Opened
            }
            // Like the engine: a transport that is already open refuses a second open.
            AudioState.Transport, AudioState.Listening, AudioState.Tone, AudioState.Metronome -> TransportOpenResult.Busy(audioState)
        }
    }

    override suspend fun closeTransport() {
        _calls += "closeTransport"
        if (audioState == AudioState.Transport) audioState = AudioState.Idle
        transportPlaying = false
        armed = false
        takeRunning = false
        nextCapture.armed = false
        nextCapture.recording = false
        nextCapture.preRoll = false
        nextCapture.monitoring = false
    }

    override suspend fun openClipSource(path: String): ClipSourceResult {
        _calls += "openClipSource($path)"
        if (path in clipSourceFailures) return ClipSourceResult.Undecodable
        val info = scriptedInfo(path) ?: return ClipSourceResult.Undecodable
        val handle = ClipSourceHandle(nextClipHandle++)
        openClipSources[handle.id] = path to info
        return ClipSourceResult.Opened(handle, info)
    }

    override fun closeClipSource(handle: ClipSourceHandle) {
        _calls += "closeClipSource(${handle.id})"
        openClipSources.remove(handle.id)
    }

    override fun setTempo(tempo: Tempo, signature: TimeSignature) {
        _calls += "setTempo(${tempo.bpm}, ${signature.label})"
    }

    override fun setArrangement(snapshot: ArrangementSnapshot) {
        _calls += "setArrangement(tracks=${snapshot.tracks.size},clips=${snapshot.clips.size})"
        lastArrangement = snapshot
    }

    override fun setTrack(index: Int, gainLinear: Float, audible: Boolean) {
        _calls += "setTrack($index,${round(gainLinear * 1000) / 1000},$audible)"
    }

    override fun setMaster(gainLinear: Float) {
        _calls += "setMaster(${round(gainLinear * 1000) / 1000})"
    }

    override fun setMetronome(enabled: Boolean) {
        _calls += "setMetronome($enabled)"
    }

    override suspend fun play(): PlayResult {
        _calls += "play"
        val failure = failNextPlay
        if (failure != null) {
            failNextPlay = null
            return failure
        }
        if (audioState != AudioState.Transport) return PlayResult.Failed(STATE_ERROR)
        transportPlaying = true
        nextTransport.playing = true
        return PlayResult.Started
    }

    override fun pause() {
        _calls += "pause"
        transportPlaying = false
        nextTransport.playing = false
    }

    override suspend fun seek(beats: Beats) {
        _calls += "seek(${beats.value})"
        nextTransport.positionBeats = beats.value
    }

    override fun readTransport(into: TransportSnapshot) {
        readTransportCount++
        into.copyFrom(nextTransport)
    }

    override suspend fun inspect(path: String): AudioInfo? {
        _calls += "inspect($path)"
        return scriptedInfo(path)
    }

    /** A listed path answers its entry, null included; an unlisted one the default. */
    private fun scriptedInfo(path: String): AudioInfo? = if (path in inspectResults) inspectResults[path] else defaultInspectResult

    override suspend fun buildOverview(audioPath: String, overviewPath: String): Boolean {
        _calls += "buildOverview($audioPath)"
        return overviewWriter?.invoke(overviewPath) ?: true
    }

    // --- Capture, takes and monitoring (feature 010, contracts/engine-c-abi-v7.md "Kotlin seam") ---------------------

    /** What [arm] answers while the transport is open. */
    var armResult: ArmResult = ArmResult.Armed(channels = 1, sampleRate = 48000)

    /** True between a successful [arm] and [disarm] (or the transport closing). */
    var armed: Boolean = false
        private set

    /** What [readCapture] copies; tests script it. [arm] sets its armed, channels and rate; [recordStart] its phase. */
    var nextCapture: CaptureSnapshot = CaptureSnapshot()

    /** How many times [readCapture] was called. */
    var readCaptureCount: Int = 0
        private set

    /** Serves [readPeaks]: fills the buffer from the index and returns the next; null serves nothing. */
    var scriptedPeaks: ((fromIndex: Long, into: PeakBuckets) -> Long)? = null

    /** What [recordStop] returns; null builds a 2 s take at the transport's position with [nextCapture]'s format. */
    var recordStopResult: TakeInfo? = null

    /** Called by [recordStart] with the staging path so a test can write a tiny valid WAV there; null writes nothing. */
    var takeWriter: ((path: String) -> Unit)? = null

    /** True between [recordStart] and [recordStop] / [recordCancel]. */
    var takeRunning: Boolean = false
        private set

    /** The last [recordStart] configuration. */
    var lastRecordConfig: RecordConfig? = null
        private set

    /** What [freeBytes] answers. */
    var freeBytesResult: Long = 1024L * 1024 * 1024

    override suspend fun arm(maxChannels: Int): ArmResult {
        _calls += "arm($maxChannels)"
        if (audioState != AudioState.Transport) return ArmResult.NotTransport
        val result = armResult
        if (result is ArmResult.Armed) {
            armed = true
            nextCapture.armed = true
            nextCapture.inputChannels = result.channels
            nextCapture.sampleRate = result.sampleRate
        }
        return result
    }

    override suspend fun disarm() {
        _calls += "disarm"
        armed = false
        takeRunning = false
        nextCapture.armed = false
        nextCapture.recording = false
        nextCapture.preRoll = false
        nextCapture.monitoring = false
        nextTransport.recording = false
        nextTransport.countInBeatsLeft = -1
    }

    override fun setMonitor(enabled: Boolean, trackIndex: Int) {
        _calls += "setMonitor($enabled,$trackIndex)"
        nextCapture.monitoring = enabled && armed
    }

    override suspend fun recordStart(config: RecordConfig): RecordStartResult {
        _calls += "recordStart(${config.wavPath},${config.trackIndex},${config.countInBeats})"
        if (!armed) return RecordStartResult.NotArmed
        if (takeRunning) return RecordStartResult.TakeRunning
        takeWriter?.invoke(config.wavPath)
        lastRecordConfig = config
        takeRunning = true
        transportPlaying = true
        nextTransport.playing = true
        nextCapture.preRoll = config.countInBeats > 0
        nextCapture.recording = config.countInBeats == 0
        nextCapture.takeStartFrames = nextTransport.positionFrames
        nextTransport.recording = nextCapture.recording
        nextTransport.countInBeatsLeft = if (config.countInBeats > 0) config.countInBeats else -1
        return RecordStartResult.Started
    }

    /** The pre-roll ended on its own: the take captures from the transport's position (not recorded as a call). */
    fun endPreRoll() {
        if (!takeRunning) return
        nextCapture.preRoll = false
        nextCapture.recording = true
        nextTransport.recording = true
        nextTransport.countInBeatsLeft = -1
    }

    /** The engine ended the take itself with [reason] (the cap, the disk, a lost input); the host collects it with [recordStop]. */
    fun endTake(reason: audio.tembra.core.recording.TakeStopReason) {
        if (!takeRunning) return
        nextCapture.recording = false
        nextCapture.preRoll = false
        nextCapture.stopReason = reason
        nextTransport.recording = false
        nextTransport.countInBeatsLeft = -1
    }

    override fun recordCancel() {
        _calls += "recordCancel"
        takeRunning = false
        transportPlaying = false
        nextTransport.playing = false
        nextTransport.recording = false
        nextTransport.countInBeatsLeft = -1
        nextCapture.recording = false
        nextCapture.preRoll = false
    }

    override suspend fun recordStop(): TakeInfo {
        _calls += "recordStop"
        takeRunning = false
        nextCapture.recording = false
        nextCapture.preRoll = false
        nextTransport.recording = false
        nextTransport.countInBeatsLeft = -1
        val reason = nextCapture.stopReason
        nextCapture.stopReason = audio.tembra.core.recording.TakeStopReason.User
        return recordStopResult ?: TakeInfo(
            startFrames = nextCapture.takeStartFrames,
            startBeats = nextTransport.positionBeats,
            frames = 2L * nextCapture.sampleRate,
            channels = nextCapture.inputChannels,
            sampleRate = nextCapture.sampleRate,
            alignmentFrames = nextCapture.alignmentFrames,
            stopReason = reason,
        )
    }

    override fun readCapture(into: CaptureSnapshot) {
        readCaptureCount++
        into.copyFrom(nextCapture)
    }

    override fun readPeaks(fromIndex: Long, into: PeakBuckets): Long {
        val script = scriptedPeaks
        if (script == null) {
            into.count = 0
            into.firstIndex = fromIndex
            into.nextIndex = fromIndex
            return fromIndex
        }
        return script(fromIndex, into)
    }

    override fun resetClip() {
        _calls += "resetClip"
        nextCapture.clipped = false
    }

    override fun freeBytes(path: String): Long = freeBytesResult

    private companion object {
        /** `TEMBRA_ERR_STATE`. */
        const val STATE_ERROR = -1
    }
}
