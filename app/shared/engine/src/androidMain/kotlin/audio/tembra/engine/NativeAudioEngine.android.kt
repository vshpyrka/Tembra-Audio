package audio.tembra.engine

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * JNI facade over `libtembra_engine.so`. Tones hold transient audio focus so other apps duck (research R7); capture
 * requests nothing (Android gives the microphone to the foreground app). Nothing here runs on an audio thread.
 */
actual class NativeAudioEngine(context: Context) : AudioEngine {

    private val focus = AndroidAudioFocus(context)

    /** Reused by every [readPitch]: [state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch, synthetic]. */
    private val reading = FloatArray(READING_FIELDS)

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
        focus.request()
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
        focus.request()
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

    override val audioState: AudioState
        get() = AudioState.fromNative(nativeAudioState())

    override val events: Flow<AudioEvent> = focus.events

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

    private companion object {
        const val READING_FIELDS = 7

        init {
            System.loadLibrary("tembra_engine")
        }
    }
}
