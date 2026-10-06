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
import audio.tembra.engine.cinterop.tembra_tuner_set_gate
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
import platform.Foundation.NSLock
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * Cinterop facade over the static engine archive. The `AVAudioSession` is configured around every stream
 * (research R3, R7): `playAndRecord` in measurement mode for listening, `playback` for tones, deactivated with
 * `notifyOthersOnDeactivation` when idle. The metronome also plays in `playback` and reports the session's output
 * latency at the start and on every route change while it runs (feature 004 R7). The six native structs live as long
 * as the engine object.
 *
 * The session is activated only by the call that moves the engine out of Idle and deactivated only when it returns
 * there (contracts/metronome-audio-kotlin.md): a refused start never deactivates under a running stream, and a stop
 * leaves the session alone while another stream runs. A preview click ends on its own, so the actual closes the output
 * and deactivates the session itself once the engine is back to Idle.
 */
@OptIn(ExperimentalForeignApi::class)
actual class NativeAudioEngine internal constructor(
    /** Where [reportOutputLatencyNanos] delivers the latency: the engine; tests record it instead. */
    private val engineLatencySink: (Long) -> Unit,
) : AudioEngine {

    constructor() : this({ nanos -> tembra_metronome_set_output_latency_ns(nanos) })

    private val reading = nativeHeap.alloc<tembra_pitch_reading>()
    private val config = nativeHeap.alloc<tembra_tuner_config>()
    private val signal = nativeHeap.alloc<tembra_test_signal>()
    private val grid = nativeHeap.alloc<tembra_metronome_grid>()
    private val tick = nativeHeap.alloc<tembra_metronome_tick>()
    private val customInfo = nativeHeap.alloc<tembra_custom_sound_info>()

    /** Guards [previewGeneration] against a start or stop racing the main-queue check. */
    private val previewLock = NSLock()

    /** Bumped by every start and releasing stop; a scheduled preview check only acts while its generation is current. */
    private var previewGeneration = 0

    /** True while a preview check is scheduled for [previewGeneration]. */
    private var previewPending = false

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
        if (audioState != AudioState.Idle) return STATE_REFUSED
        cancelPreviewRelease()
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
        releaseSessionUnlessOwnedBy(AudioState.Tone, AudioState.Metronome)
    }

    /** The engine keeps the value; the audio session is not touched, so a change while listening interrupts nothing. */
    override fun setNoiseGate(openDb: Float?): AudioResult =
        AudioResult.fromNative(tembra_tuner_set_gate(if (openDb != null) 1u else 0u, openDb ?: 0f))

    /**
     * Deactivates the session unless the engine is (still) in one of [owners], whose own stop releases it. Also cancels
     * a pending preview check, so the session is never deactivated twice.
     */
    private fun releaseSessionUnlessOwnedBy(vararg owners: AudioState) {
        if (audioState in owners) return
        cancelPreviewRelease()
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
        into.inputLevelDb = reading.input_level_db
        into.gateOpen = reading.gate_open.toInt() != 0
    }

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult =
        sound { tembra_tone_start(frequencyHz, durationMs.toUInt()) }

    /**
     * Tones and samples: from Idle they activate `playback`; over a sounding tone the engine re-arms it in the session
     * already active; while listening or the metronome runs the engine refuses, and the session is not touched.
     */
    private inline fun sound(start: () -> Int): AudioResult {
        when (audioState) {
            AudioState.Listening, AudioState.Metronome -> return STATE_REFUSED
            AudioState.Tone -> return AudioResult.fromNative(start())
            AudioState.Idle -> Unit
        }
        cancelPreviewRelease()
        IosAudioSession.activateForPlayback()
        val result = AudioResult.fromNative(start())
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

    override fun playSample(handle: SampleHandle, loop: Boolean): AudioResult =
        sound { tembra_sample_play(handle.id, if (loop) 1u else 0u) }

    override fun unloadSample(handle: SampleHandle) {
        tembra_sample_unload(handle.id)
    }

    override fun stopTone() {
        tembra_tone_stop()
        releaseSessionUnlessOwnedBy(AudioState.Listening, AudioState.Metronome)
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
        if (audioState != AudioState.Idle) return STATE_REFUSED
        cancelPreviewRelease()
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
        tembra_metronome_stop()
        if (audioState == AudioState.Idle) IosAudioSession.routeChangeListener = null
        releaseSessionUnlessOwnedBy(AudioState.Listening, AudioState.Tone)
    }

    override fun previewClick(level: ClickLevel): AudioResult {
        if (audioState != AudioState.Idle) return STATE_REFUSED
        cancelPreviewRelease()
        IosAudioSession.activateForPlayback()
        val result = AudioResult.fromNative(tembra_metronome_preview(level.native.toUInt()))
        if (result == AudioResult.Ok) schedulePreviewRelease() else IosAudioSession.deactivate()
        return result
    }

    /**
     * Checks the engine state on the main queue every [PREVIEW_CHECK_INTERVAL_NANOS] and, once the click has ended,
     * closes the output unit the preview opened (the engine's tone stop, the documented end of a preview; a running unit
     * would make the deactivation fail as busy) and deactivates the session (`notifyOthersOnDeactivation`). Gives up
     * (leaving the unit and session to the next stop) after [PREVIEW_CHECKS] checks.
     */
    private fun schedulePreviewRelease() {
        previewLock.lock()
        val generation = ++previewGeneration
        previewPending = true
        previewLock.unlock()
        checkPreviewLater(generation, PREVIEW_CHECKS)
    }

    private fun checkPreviewLater(generation: Int, remaining: Int) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, PREVIEW_CHECK_INTERVAL_NANOS), dispatch_get_main_queue()) {
            previewLock.lock()
            try {
                if (generation != previewGeneration || !previewPending) return@dispatch_after
                when (audioState) {
                    AudioState.Idle -> {
                        previewPending = false
                        tembra_tone_stop()  // closes the unit the preview left open; the session cannot deactivate under it
                        IosAudioSession.deactivate()
                    }
                    AudioState.Tone ->
                        if (remaining > 1) checkPreviewLater(generation, remaining - 1) else previewPending = false
                    // Another stream took over; every start cancels this check first, so this is only a safeguard.
                    AudioState.Listening, AudioState.Metronome -> previewPending = false
                }
            } finally {
                previewLock.unlock()
            }
        }
    }

    private fun cancelPreviewRelease() {
        previewLock.lock()
        previewGeneration++
        previewPending = false
        previewLock.unlock()
    }

    /** 0 means "measure the current route again": the session's latency is forwarded, never a 0 that would wipe it. */
    override fun reportOutputLatencyNanos(nanos: Long) {
        engineLatencySink(if (nanos > 0) nanos else IosAudioSession.outputLatencyNanos())
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

    private companion object {
        /** `TEMBRA_ERR_STATE`: what the engine answers when it is not in a state that allows the call. */
        val STATE_REFUSED = AudioResult.Error(-1)
        const val PREVIEW_CHECK_INTERVAL_NANOS = 100_000_000L

        /** 3 s: a custom sound is kept up to 2 s (research R4), so a 1 s bound would leave its session active. */
        const val PREVIEW_CHECKS = 30
    }
}

private const val GRID_LEVELS = 32
