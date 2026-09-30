package audio.tembra.engine

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

/**
 * JNI facade over `libtembra_engine.so`. Tones and the preview click hold transient audio focus so other apps duck
 * (research R7); the running metronome holds full focus and listens for headphones being unplugged (feature 004 R6);
 * capture requests nothing (Android gives the microphone to the foreground app). Nothing here runs on an audio thread.
 */
actual class NativeAudioEngine(context: Context) : AudioEngine {

    private val focus = AndroidAudioFocus(context)

    private val routeEvents = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)
    private val becomingNoisy = BecomingNoisyReceiver(context) { routeEvents.tryEmit(AudioEvent.OutputRouteLost) }

    /** Reused by every [readPitch]: [state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch, synthetic]. */
    private val reading = FloatArray(READING_FIELDS)

    /** Reused by every [readTick]: [tickIndex, beatIndex, level, audibleInNs, beatPeriodNs, running, sampleRate]. */
    private val tickFields = LongArray(TICK_FIELDS)

    /** The C ABI revision this library was built with; tests check it. */
    internal fun abiVersion(): Int = nativeAbiVersion()

    override fun describe(): EngineDescriptor = EngineDescriptorParser.parse(nativeDescribe())

    override fun startListening(config: TunerAnalysisConfig): AudioResult = AudioResult.fromNative(
        nativeTunerStart(
            config.windowFrames, config.hopFrames, config.minHz, config.maxHz,
            config.clarityThreshold, config.peakRatio, config.medianSize, config.watchdogSeconds,
        ),
    )

    override fun startTestSignal(signal: TestSignal, config: TunerAnalysisConfig): AudioResult = AudioResult.fromNative(
        nativeTunerStartTestSignal(
            signal.frequencyHz, signal.amplitude, signal.harmonics, signal.fundamentalGain,
            config.windowFrames, config.hopFrames, config.minHz, config.maxHz,
            config.clarityThreshold, config.peakRatio, config.medianSize, config.watchdogSeconds,
        ),
    )

    override fun stopListening() {
        nativeTunerStop()
    }

    override fun readPitch(into: PitchSnapshot) {
        val sequence = nativeTunerRead(reading)
        into.sequence = sequence
        into.state = AudioState.fromNative(reading[0].toInt())
        into.sampleRate = reading[1].toInt()
        into.frequencyHz = reading[2]
        into.clarity = reading[3]
        into.inputPeak = reading[4]
        into.hasPitch = reading[5] != 0f
        into.synthetic = reading[6] != 0f
    }

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult {
        focus.request(FocusType.TransientMayDuck)
        val result = AudioResult.fromNative(nativeToneStart(frequencyHz, durationMs))
        if (result != AudioResult.Ok) focus.abandon()
        return result
    }

    override fun loadSample(wavBytes: ByteArray): SampleHandle? {
        if (wavBytes.isEmpty()) return null
        val handle = nativeSampleLoadWav(wavBytes)
        return if (handle < 0) null else SampleHandle(handle)
    }

    override fun playSample(handle: SampleHandle, loop: Boolean): AudioResult {
        focus.request(FocusType.TransientMayDuck)
        val result = AudioResult.fromNative(nativeSamplePlay(handle.id, loop))
        if (result != AudioResult.Ok) focus.abandon()
        return result
    }

    override fun unloadSample(handle: SampleHandle) {
        nativeSampleUnload(handle.id)
    }

    override fun stopTone() {
        nativeToneStop()
        focus.abandon()
    }

    override fun setGrid(grid: MetronomeGrid): AudioResult {
        // The engine validates the size (at most 32 levels, whole beats); the JNI copies at most 32 levels.
        val levels = ByteArray(grid.levels.size) { grid.levels[it].native.toByte() }
        return AudioResult.fromNative(nativeMetronomeSetGrid(grid.tempo.bpm.toDouble(), grid.ticksPerBeat, grid.levels.size, levels))
    }

    override fun selectSound(sound: ClickSoundId): AudioResult = AudioResult.fromNative(nativeMetronomeSelectSound(sound.native))

    override fun loadCustomSound(path: String): CustomSoundResult {
        // Allocated per call: loading runs rarely and off the main thread, so a shared buffer could race.
        val info = IntArray(CUSTOM_INFO_FIELDS)
        val ms = FloatArray(CUSTOM_MS_FIELDS)
        val code = nativeMetronomeLoadCustomSound(path, info, ms)
        if (code != 0) return CustomSoundResult.fromNativeError(code)
        return CustomSoundResult.Loaded(
            CustomSoundInfo(
                sourceRate = info[0],
                sourceChannels = info[1],
                sourceBits = info[2],
                keptFrames = info[3],
                trimmedMs = ms[0],
                keptMs = ms[1],
            ),
        )
    }

    override fun unloadCustomSound() {
        nativeMetronomeUnloadCustomSound()
    }

    override fun startMetronome(): AudioResult {
        if (!focus.request(FocusType.Gain)) return AudioResult.Error(AudioResult.FOCUS_DENIED)
        becomingNoisy.register()
        val result = AudioResult.fromNative(nativeMetronomeStart())
        if (result != AudioResult.Ok) {
            becomingNoisy.unregister()
            focus.abandon()
        }
        return result
    }

    override fun stopMetronome() {
        nativeMetronomeStop()
        becomingNoisy.unregister()
        focus.abandon()
    }

    override fun previewClick(level: ClickLevel): AudioResult {
        focus.request(FocusType.TransientMayDuck)
        val result = AudioResult.fromNative(nativeMetronomePreview(level.native))
        if (result != AudioResult.Ok) focus.abandon()
        return result
    }

    override fun reportOutputLatencyNanos(nanos: Long) {
        // 0 asks the device to measure again (the cache taken right after the stream start is often empty).
        nativeMetronomeSetOutputLatencyNs(nanos)
    }

    override fun readTick(into: TickSnapshot) {
        nativeMetronomeRead(tickFields)
        into.tickIndex = tickFields[0]
        into.beatIndex = tickFields[1].toInt()
        into.level = ClickLevel.fromNative(tickFields[2].toInt())
        into.audibleInNanos = tickFields[3]
        into.beatPeriodNanos = tickFields[4]
        into.running = tickFields[5] != 0L
        into.sampleRate = tickFields[6].toInt()
    }

    override val audioState: AudioState
        get() = AudioState.fromNative(nativeAudioState())

    override val events: Flow<AudioEvent> = merge(focus.events, routeEvents)

    private external fun nativeAbiVersion(): Int
    private external fun nativeName(): String
    private external fun nativeVersion(): String
    private external fun nativeDescribe(): String
    private external fun nativeTunerStart(
        window: Int, hop: Int, minHz: Float, maxHz: Float, clarity: Float, peakRatio: Float, median: Int, watchdog: Float,
    ): Int
    private external fun nativeTunerStartTestSignal(
        hz: Double, amplitude: Float, harmonics: Int, fundamentalGain: Float,
        window: Int, hop: Int, minHz: Float, maxHz: Float, clarity: Float, peakRatio: Float, median: Int, watchdog: Float,
    ): Int
    private external fun nativeTunerStop()
    private external fun nativeTunerRead(out: FloatArray): Long
    private external fun nativeToneStart(hz: Double, durationMs: Int): Int
    private external fun nativeSampleLoadWav(bytes: ByteArray): Int
    private external fun nativeSamplePlay(handle: Int, loop: Boolean): Int
    private external fun nativeSampleUnload(handle: Int)
    private external fun nativeToneStop()
    private external fun nativeAudioState(): Int
    private external fun nativeMetronomeSetGrid(bpm: Double, ticksPerBeat: Int, ticksPerBar: Int, levels: ByteArray): Int
    private external fun nativeMetronomeSelectSound(sound: Int): Int
    private external fun nativeMetronomeLoadCustomSound(path: String, info: IntArray, ms: FloatArray): Int
    private external fun nativeMetronomeUnloadCustomSound()
    private external fun nativeMetronomeStart(): Int
    private external fun nativeMetronomeStop()
    private external fun nativeMetronomePreview(level: Int): Int
    private external fun nativeMetronomeSetOutputLatencyNs(nanos: Long)
    private external fun nativeMetronomeRead(out: LongArray)

    private companion object {
        const val READING_FIELDS = 7
        const val TICK_FIELDS = 7
        const val CUSTOM_INFO_FIELDS = 4
        const val CUSTOM_MS_FIELDS = 2

        init {
            System.loadLibrary("tembra_engine")
        }
    }
}
