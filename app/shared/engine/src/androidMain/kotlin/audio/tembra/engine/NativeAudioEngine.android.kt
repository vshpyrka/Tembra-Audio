package audio.tembra.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

/**
 * JNI facade over `libtembra_engine.so`. Tones and the preview click hold transient audio focus so other apps duck
 * (research R7); the running metronome holds full focus and listens for headphones being unplugged (feature 004 R6);
 * capture requests nothing (Android gives the microphone to the foreground app). Nothing here runs on an audio thread.
 *
 * Focus and the becoming-noisy receiver are acquired only by the call that moves the engine out of Idle and released
 * only when it returns there (contracts/metronome-audio-kotlin.md): a refused start never touches what the running
 * stream holds, and a stop leaves alone what another running stream holds. A preview click ends on its own, so the
 * actual closes its output stream and releases its focus itself once the engine is back to Idle.
 */
actual class NativeAudioEngine(context: Context) : AudioEngine {

    /** Internal so device tests can observe what is held. */
    internal val focus = AndroidAudioFocus(context)

    private val routeEvents = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 16)

    /** Internal so device tests can observe the registration. */
    internal val becomingNoisy = BecomingNoisyReceiver(context) { routeEvents.tryEmit(AudioEvent.OutputRouteLost) }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Guards [previewRelease] against a start or stop racing the main-thread check. */
    private val previewLock = Any()

    /** The pending check that releases a preview's focus; null when none is scheduled. */
    private var previewRelease: Runnable? = null

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

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult = sound { nativeToneStart(frequencyHz, durationMs) }

    /**
     * Tones and samples: from Idle they take transient focus; over a sounding tone the engine re-arms it and the focus
     * it already holds stays; while listening or the metronome runs the engine refuses, and nothing is touched.
     */
    private inline fun sound(start: () -> Int): AudioResult {
        when (audioState) {
            AudioState.Listening, AudioState.Metronome -> return STATE_REFUSED
            AudioState.Tone -> return AudioResult.fromNative(start())
            AudioState.Idle -> Unit
        }
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
        focus.request(FocusType.TransientMayDuck)
        val result = AudioResult.fromNative(start())
        if (result != AudioResult.Ok) focus.abandon()
        return result
    }

    override fun loadSample(wavBytes: ByteArray): SampleHandle? {
        if (wavBytes.isEmpty()) return null
        val handle = nativeSampleLoadWav(wavBytes)
        return if (handle < 0) null else SampleHandle(handle)
    }

    override fun playSample(handle: SampleHandle, loop: Boolean): AudioResult = sound { nativeSamplePlay(handle.id, loop) }

    override fun unloadSample(handle: SampleHandle) {
        nativeSampleUnload(handle.id)
    }

    override fun stopTone() {
        nativeToneStop()
        // A running metronome owns the focus; otherwise release what a tone, sample or preview left behind.
        if (audioState == AudioState.Metronome) return
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
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
        if (audioState != AudioState.Idle) return STATE_REFUSED
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
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
        // A tone or preview still sounding owns the focus (its own stop or the preview check releases it).
        if (audioState == AudioState.Tone) return
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
        focus.abandon()
    }

    override fun previewClick(level: ClickLevel): AudioResult {
        if (audioState != AudioState.Idle) return STATE_REFUSED
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
        focus.request(FocusType.TransientMayDuck)
        val result = AudioResult.fromNative(nativeMetronomePreview(level.native))
        if (result == AudioResult.Ok) schedulePreviewRelease() else focus.abandon()
        return result
    }

    /**
     * Checks the engine state on the main thread every [PREVIEW_CHECK_INTERVAL_MS] and, once the click has ended, closes
     * the output stream the preview opened (the engine's tone stop, the documented end of a preview) and abandons the
     * preview's focus. Gives up (keeping the stream and focus for the next stop) after [PREVIEW_CHECKS] checks.
     */
    private fun schedulePreviewRelease() {
        val check = object : Runnable {
            private var remaining = PREVIEW_CHECKS

            override fun run() {
                synchronized(previewLock) {
                    if (previewRelease !== this) return
                    when (audioState) {
                        AudioState.Idle -> {
                            previewRelease = null
                            nativeToneStop()  // closes the stream the preview left open, which otherwise renders silence
                            focus.abandon()
                        }
                        AudioState.Tone ->
                            if (--remaining > 0) mainHandler.postDelayed(this, PREVIEW_CHECK_INTERVAL_MS) else previewRelease = null
                        // Another stream took over; every start cancels this check first, so this is only a safeguard.
                        AudioState.Listening, AudioState.Metronome -> previewRelease = null
                    }
                }
            }
        }
        synchronized(previewLock) {
            previewRelease = check
            mainHandler.postDelayed(check, PREVIEW_CHECK_INTERVAL_MS)
        }
    }

    private fun cancelPreviewReleaseLocked() {
        previewRelease?.let(mainHandler::removeCallbacks)
        previewRelease = null
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
        const val PREVIEW_CHECK_INTERVAL_MS = 100L

        /** 3 s: a custom sound is kept up to 2 s (research R4), so a 1 s bound would leave its focus held. */
        const val PREVIEW_CHECKS = 30

        /** `TEMBRA_ERR_STATE`: what the engine answers when it is not in a state that allows the call. */
        val STATE_REFUSED = AudioResult.Error(-1)

        init {
            System.loadLibrary("tembra_engine")
        }
    }
}
