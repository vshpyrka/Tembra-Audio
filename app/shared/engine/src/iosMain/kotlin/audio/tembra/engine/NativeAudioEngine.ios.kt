package audio.tembra.engine

import audio.tembra.engine.cinterop.tembra_audio_state
import audio.tembra.engine.cinterop.tembra_custom_sound_info
import audio.tembra.engine.cinterop.tembra_engine_abi_version
import audio.tembra.engine.cinterop.tembra_engine_describe
import audio.tembra.engine.cinterop.tembra_metronome_grid
import audio.tembra.engine.cinterop.tembra_metronome_load_custom_sound
import audio.tembra.engine.cinterop.tembra_metronome_preview
import audio.tembra.engine.cinterop.tembra_metronome_read
import audio.tembra.engine.cinterop.tembra_metronome_select_sound
import audio.tembra.engine.cinterop.tembra_metronome_set_grid
import audio.tembra.engine.cinterop.tembra_metronome_set_output_latency_ns
import audio.tembra.engine.cinterop.tembra_metronome_start
import audio.tembra.engine.cinterop.tembra_metronome_stop
import audio.tembra.engine.cinterop.tembra_metronome_tick
import audio.tembra.engine.cinterop.tembra_metronome_unload_custom_sound
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
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow

/**
 * Cinterop facade over the static engine archive. The `AVAudioSession` is configured around every stream
 * (research R3, R7): `playAndRecord` in measurement mode for listening, `playback` for tones, deactivated with
 * `notifyOthersOnDeactivation` when idle. The metronome also plays in `playback` and reports the session's output
 * latency at the start and on every route change while it runs (feature 004 R7). The six native structs live as long
 * as the engine object.
 */
@OptIn(ExperimentalForeignApi::class)
actual class NativeAudioEngine : AudioEngine {

    private val reading = nativeHeap.alloc<tembra_pitch_reading>()
    private val config = nativeHeap.alloc<tembra_tuner_config>()
    private val signal = nativeHeap.alloc<tembra_test_signal>()
    private val grid = nativeHeap.alloc<tembra_metronome_grid>()
    private val tick = nativeHeap.alloc<tembra_metronome_tick>()
    private val customInfo = nativeHeap.alloc<tembra_custom_sound_info>()

    /** The C ABI revision this library was built with; tests check it. */
    internal fun abiVersion(): Int = tembra_engine_abi_version().toInt()

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

    override fun setGrid(grid: MetronomeGrid): AudioResult {
        // The engine validates the size (at most 32 levels, whole beats); the struct holds at most 32 levels.
        this.grid.bpm = grid.tempo.bpm.toDouble()
        this.grid.ticks_per_beat = grid.ticksPerBeat.toUInt()
        this.grid.ticks_per_bar = grid.levels.size.toUInt()
        for (index in 0 until GRID_LEVELS) {
            this.grid.levels[index] = grid.levels.getOrNull(index)?.native?.toUByte() ?: 0u
        }
        return AudioResult.fromNative(tembra_metronome_set_grid(this.grid.ptr))
    }

    override fun selectSound(sound: ClickSoundId): AudioResult = AudioResult.fromNative(tembra_metronome_select_sound(sound.native.toUInt()))

    override fun loadCustomSound(path: String): CustomSoundResult {
        val code = tembra_metronome_load_custom_sound(path, customInfo.ptr)
        if (code != 0) return CustomSoundResult.fromNativeError(code)
        return CustomSoundResult.Loaded(
            CustomSoundInfo(
                sourceRate = customInfo.source_rate.toInt(),
                sourceChannels = customInfo.source_channels.toInt(),
                sourceBits = customInfo.source_bits.toInt(),
                keptFrames = customInfo.kept_frames.toInt(),
                trimmedMs = customInfo.trimmed_ms,
                keptMs = customInfo.kept_ms,
            ),
        )
    }

    override fun unloadCustomSound() {
        tembra_metronome_unload_custom_sound()
    }

    override fun startMetronome(): AudioResult {
        IosAudioSession.activateForPlayback()
        reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos())
        val result = AudioResult.fromNative(tembra_metronome_start())
        if (result == AudioResult.Ok) {
            IosAudioSession.routeChangeListener = { reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos()) }
        } else {
            IosAudioSession.deactivate()
        }
        return result
    }

    override fun stopMetronome() {
        IosAudioSession.routeChangeListener = null
        tembra_metronome_stop()
        IosAudioSession.deactivate()
    }

    override fun previewClick(level: ClickLevel): AudioResult {
        IosAudioSession.activateForPlayback()
        val result = AudioResult.fromNative(tembra_metronome_preview(level.native.toUInt()))
        if (result != AudioResult.Ok) IosAudioSession.deactivate()
        return result
    }

    override fun reportOutputLatencyNanos(nanos: Long) {
        tembra_metronome_set_output_latency_ns(nanos)
    }

    override fun readTick(into: TickSnapshot) {
        tembra_metronome_read(tick.ptr)
        into.tickIndex = tick.tick_index.toLong()
        into.beatIndex = tick.beat_index.toInt()
        into.level = ClickLevel.fromNative(tick.level.toInt())
        into.audibleInNanos = tick.audible_in_ns
        into.beatPeriodNanos = tick.beat_period_ns
        into.running = tick.running != 0u
        into.sampleRate = tick.sample_rate.toInt()
    }

    override val audioState: AudioState
        get() = AudioState.fromNative(tembra_audio_state().toInt())

    override val events: Flow<AudioEvent> = IosAudioSession.events
}

private const val GRID_LEVELS = 32
