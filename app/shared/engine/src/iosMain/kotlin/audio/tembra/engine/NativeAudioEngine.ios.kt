package audio.tembra.engine

import audio.tembra.engine.cinterop.tembra_audio_state
import audio.tembra.engine.cinterop.tembra_engine_describe
import audio.tembra.engine.cinterop.tembra_pitch_reading
import audio.tembra.engine.cinterop.tembra_sample_load_wav
import audio.tembra.engine.cinterop.tembra_sample_play
import audio.tembra.engine.cinterop.tembra_sample_unload
import audio.tembra.engine.cinterop.tembra_test_signal
import audio.tembra.engine.cinterop.tembra_tone_start
import audio.tembra.engine.cinterop.tembra_tone_stop
import audio.tembra.engine.cinterop.tembra_tuner_config
import audio.tembra.engine.cinterop.tembra_tuner_read
import audio.tembra.engine.cinterop.tembra_tuner_start
import audio.tembra.engine.cinterop.tembra_tuner_start_test_signal
import audio.tembra.engine.cinterop.tembra_tuner_stop
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow

/**
 * Cinterop facade over the static engine archive. The `AVAudioSession` is configured around every stream
 * (research R3, R7): `playAndRecord` in measurement mode for listening, `playback` for tones, deactivated with
 * `notifyOthersOnDeactivation` when idle. The three native structs live as long as the engine object.
 */
@OptIn(ExperimentalForeignApi::class)
actual class NativeAudioEngine : AudioEngine {

    private val reading = nativeHeap.alloc<tembra_pitch_reading>()
    private val config = nativeHeap.alloc<tembra_tuner_config>()
    private val signal = nativeHeap.alloc<tembra_test_signal>()

    override fun describe(): EngineDescriptor {
        val raw = tembra_engine_describe()?.toKString() ?: error("tembra_engine_describe() returned null")
        return EngineDescriptorParser.parse(raw)
    }

    private fun fill(source: TunerAnalysisConfig) {
        config.window_frames = source.windowFrames.toUInt()
        config.hop_frames = source.hopFrames.toUInt()
        config.min_hz = source.minHz
        config.max_hz = source.maxHz
        config.clarity_threshold = source.clarityThreshold
        config.peak_ratio = source.peakRatio
        config.median_size = source.medianSize.toUInt()
        config.watchdog_seconds = source.watchdogSeconds
    }

    override fun startListening(config: TunerAnalysisConfig): AudioResult {
        fill(config)
        IosAudioSession.activateForListening()
        val result = AudioResult.fromNative(tembra_tuner_start(this.config.ptr))
        if (result != AudioResult.Ok) IosAudioSession.deactivate()
        return result
    }

    override fun startTestSignal(signal: TestSignal, config: TunerAnalysisConfig): AudioResult {
        fill(config)
        this.signal.frequency_hz = signal.frequencyHz
        this.signal.amplitude = signal.amplitude
        this.signal.harmonics = signal.harmonics.toUInt()
        this.signal.fundamental_gain = signal.fundamentalGain
        return AudioResult.fromNative(tembra_tuner_start_test_signal(this.config.ptr, this.signal.ptr))
    }

    override fun stopListening() {
        tembra_tuner_stop()
        IosAudioSession.deactivate()
    }

    override fun readPitch(into: PitchSnapshot) {
        tembra_tuner_read(reading.ptr)
        into.sequence = reading.sequence.toLong()
        into.state = AudioState.fromNative(reading.state.toInt())
        into.sampleRate = reading.sample_rate.toInt()
        into.frequencyHz = reading.frequency_hz
        into.clarity = reading.clarity
        into.inputPeak = reading.input_peak
        into.hasPitch = reading.has_pitch.toInt() != 0
        into.synthetic = reading.synthetic.toInt() != 0
    }

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult {
        IosAudioSession.activateForPlayback()
        val result = AudioResult.fromNative(tembra_tone_start(frequencyHz, durationMs.toUInt()))
        if (result != AudioResult.Ok) IosAudioSession.deactivate()
        return result
    }

    override fun loadSample(wavBytes: ByteArray): SampleHandle? {
        if (wavBytes.isEmpty()) return null
        val handle = wavBytes.usePinned { pinned ->
            tembra_sample_load_wav(pinned.addressOf(0).reinterpret(), wavBytes.size.toUInt())
        }
        return if (handle < 0) null else SampleHandle(handle)
    }

    override fun playSample(handle: SampleHandle, loop: Boolean): AudioResult {
        IosAudioSession.activateForPlayback()
        val result = AudioResult.fromNative(tembra_sample_play(handle.id, if (loop) 1u else 0u))
        if (result != AudioResult.Ok) IosAudioSession.deactivate()
        return result
    }

    override fun unloadSample(handle: SampleHandle) {
        tembra_sample_unload(handle.id)
    }

    override fun stopTone() {
        tembra_tone_stop()
        IosAudioSession.deactivate()
    }

    override val audioState: AudioState
        get() = AudioState.fromNative(tembra_audio_state().toInt())

    override val events: Flow<AudioEvent> = IosAudioSession.events
}
