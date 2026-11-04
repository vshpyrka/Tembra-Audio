package audio.tembra.engine

import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.engine.cinterop.tembra_arrangement
import audio.tembra.engine.cinterop.tembra_capture_config
import audio.tembra.engine.cinterop.tembra_capture_read
import audio.tembra.engine.cinterop.tembra_capture_read_peaks
import audio.tembra.engine.cinterop.tembra_capture_reset_clip
import audio.tembra.engine.cinterop.tembra_capture_set_input_latency_ns
import audio.tembra.engine.cinterop.tembra_capture_snapshot
import audio.tembra.engine.cinterop.tembra_peak_bucket
import audio.tembra.engine.cinterop.tembra_record_config
import audio.tembra.engine.cinterop.tembra_storage_free_bytes
import audio.tembra.engine.cinterop.tembra_take_info
import audio.tembra.engine.cinterop.tembra_transport_arm
import audio.tembra.engine.cinterop.tembra_transport_disarm
import audio.tembra.engine.cinterop.tembra_transport_record_cancel
import audio.tembra.engine.cinterop.tembra_transport_record_start
import audio.tembra.engine.cinterop.tembra_transport_record_stop
import audio.tembra.engine.cinterop.tembra_transport_set_monitor
import audio.tembra.engine.cinterop.tembra_audio_health
import audio.tembra.engine.cinterop.tembra_audio_info
import audio.tembra.engine.cinterop.tembra_audio_inspect
import audio.tembra.engine.cinterop.tembra_audio_overview_build
import audio.tembra.engine.cinterop.tembra_audio_state
import audio.tembra.engine.cinterop.tembra_clip_info
import audio.tembra.engine.cinterop.tembra_clip_source_close
import audio.tembra.engine.cinterop.tembra_clip_source_open
import audio.tembra.engine.cinterop.tembra_transport_close
import audio.tembra.engine.cinterop.tembra_transport_config
import audio.tembra.engine.cinterop.tembra_transport_open
import audio.tembra.engine.cinterop.tembra_transport_pause
import audio.tembra.engine.cinterop.tembra_transport_play
import audio.tembra.engine.cinterop.tembra_transport_read
import audio.tembra.engine.cinterop.tembra_transport_seek_beats
import audio.tembra.engine.cinterop.tembra_transport_set_arrangement
import audio.tembra.engine.cinterop.tembra_transport_set_master
import audio.tembra.engine.cinterop.tembra_transport_set_metronome
import audio.tembra.engine.cinterop.tembra_transport_set_tempo
import audio.tembra.engine.cinterop.tembra_transport_set_track
import audio.tembra.engine.cinterop.tembra_transport_snapshot
import audio.tembra.engine.perf.AudioHealth
import audio.tembra.engine.cinterop.tembra_engine_audio_health
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
import kotlinx.coroutines.delay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.ULongVar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import platform.Foundation.NSLock
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * Cinterop facade over the static engine archive. The `AVAudioSession` is configured around every stream
 * (research R3, R7): `playAndRecord` in measurement mode for listening, `playback` for tones, deactivated with
 * `notifyOthersOnDeactivation` when idle. The metronome also plays in `playback` and reports the session's output
 * latency at the start and on every route change while it runs (feature 004 R7), as does the project transport
 * (feature 009), whose arrangement struct is allocated once and refilled. The native structs live as long as the engine
 * object.
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
    /**
     * Where [arm] and [disarm] run their session switch and unit restart (feature 010, T100): serial, so a close never
     * overlaps an open and both keep their launch order; never the caller's thread, which is the main-immediate session
     * scope. Tests inject a counting dispatcher.
     */
    private val blocking: CoroutineDispatcher = EngineDispatchers.blocking,
) : AudioEngine {

    constructor() : this({ nanos -> tembra_metronome_set_output_latency_ns(nanos) })

    private val reading = nativeHeap.alloc<tembra_pitch_reading>()
    private val config = nativeHeap.alloc<tembra_tuner_config>()
    private val signal = nativeHeap.alloc<tembra_test_signal>()
    private val grid = nativeHeap.alloc<tembra_metronome_grid>()
    private val tick = nativeHeap.alloc<tembra_metronome_tick>()
    private val customInfo = nativeHeap.alloc<tembra_custom_sound_info>()
    private val transportConfig = nativeHeap.alloc<tembra_transport_config>()
    private val arrangement = nativeHeap.alloc<tembra_arrangement>()
    private val transport = nativeHeap.alloc<tembra_transport_snapshot>()
    // Feature 010: the capture structs, allocated once (the snapshot is read every frame).
    private val captureConfig = nativeHeap.alloc<tembra_capture_config>()
    private val capture = nativeHeap.alloc<tembra_capture_snapshot>()
    private val peakBuckets = nativeHeap.allocArray<tembra_peak_bucket>(PeakBuckets.DEFAULT_CAPACITY)
    private val peakNext = nativeHeap.alloc<ULongVar>()

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
        releaseSessionUnlessOwnedBy(AudioState.Tone, AudioState.Metronome, AudioState.Transport)
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
            AudioState.Listening, AudioState.Metronome, AudioState.Transport -> return STATE_REFUSED
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
        releaseSessionUnlessOwnedBy(AudioState.Listening, AudioState.Metronome, AudioState.Transport)
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
        releaseSessionUnlessOwnedBy(AudioState.Listening, AudioState.Tone, AudioState.Transport)
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
                    AudioState.Listening, AudioState.Metronome, AudioState.Transport -> previewPending = false
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

    override fun audioHealth(): AudioHealth? = memScoped {
        val health = alloc<tembra_audio_health>()
        if (tembra_engine_audio_health(health.ptr) != 0) return null
        AudioHealth(
            callbacks = health.callbacks.toLong(),
            lastMicros = health.last_us.toInt(),
            maxMicros = health.max_us.toInt(),
            overBudget = health.over_budget.toInt(),
            xruns = health.xruns.toInt(),
            realtime = health.realtime.toInt() != 0,
            avgMicros = health.avg_us.toInt(),
            starvation = health.starvation.toInt(),
            clipsActive = health.clips_active.toInt(),
        )
    }

    override val events: Flow<AudioEvent> = IosAudioSession.events

    // --- Transport (feature 009, contracts/timeline-audio-kotlin.md) -------------------------------------------------

    /**
     * From Idle only: `playback` session, the route's latency reported for both renderers, then the engine; a refusal
     * deactivates again. The session activation and the RemoteIO unit's create, initialise and start run on the
     * [blocking] dispatcher (T102), never the caller's thread.
     */
    override suspend fun openTransport(): TransportOpenResult = withContext(blocking) {
        val state = audioState
        if (state != AudioState.Idle) return@withContext TransportOpenResult.Busy(state)
        cancelPreviewRelease()
        IosAudioSession.activateForPlayback()
        reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos())
        transportConfig.reserved = 0u
        val code = tembra_transport_open(transportConfig.ptr)
        if (code != 0) {
            IosAudioSession.deactivate()
            return@withContext if (code == STATE_REFUSED.code) TransportOpenResult.Busy(audioState) else TransportOpenResult.DeviceFailed
        }
        IosAudioSession.routeChangeListener = { reportLatencies() }
        TransportOpenResult.Opened
    }

    /** Both latencies of the current route (feature 010, research R9): the output for the clock, the input for the alignment. */
    private fun reportLatencies() {
        reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos())
        if (armed) tembra_capture_set_input_latency_ns(IosAudioSession.inputLatencyNanos())
    }

    /** The unit stops and is disposed and the session is released, on the [blocking] dispatcher (T102). */
    override suspend fun closeTransport() = withContext(blocking) {
        tembra_transport_close()
        armed = false
        if (audioState == AudioState.Idle) IosAudioSession.routeChangeListener = null
        releaseSessionUnlessOwnedBy(AudioState.Listening, AudioState.Tone, AudioState.Metronome)
    }

    override suspend fun openClipSource(path: String): ClipSourceResult = withContext(Dispatchers.IO) {
        memScoped {
            val info = alloc<tembra_clip_info>()
            val handle = tembra_clip_source_open(path, info.ptr)
            val format = AudioFormat.fromNative(info.format.toInt())
            if (handle < 0 || format == null) {
                ClipSourceResult.Undecodable
            } else {
                ClipSourceResult.Opened(
                    ClipSourceHandle(handle),
                    AudioInfo(format, channels = info.channels.toInt(), sampleRate = info.sample_rate.toInt(), frames = info.frames),
                )
            }
        }
    }

    override fun closeClipSource(handle: ClipSourceHandle) {
        tembra_clip_source_close(handle.id)
    }

    override fun setTempo(tempo: Tempo, signature: TimeSignature) {
        tembra_transport_set_tempo(tempo.bpm.toUInt(), signature.beats.toUInt(), beatUnitOf(signature.unit))
    }

    override fun setArrangement(snapshot: ArrangementSnapshot) {
        arrangement.track_count = snapshot.tracks.size.toUInt()
        arrangement.clip_count = snapshot.clips.size.toUInt()
        snapshot.tracks.forEachIndexed { index, track ->
            arrangement.tracks[index].gain_linear = track.gainLinear
            arrangement.tracks[index].audible = if (track.audible) 1u else 0u
        }
        snapshot.clips.forEachIndexed { index, clip ->
            val state = arrangement.clips[index]
            state.track = clip.trackIndex.toUInt()
            state.source = clip.source.id
            state.start_beats = clip.startBeats
            state.offset_frames = clip.offsetFrames
            state.length_frames = clip.lengthFrames
        }
        tembra_transport_set_arrangement(arrangement.ptr)
    }

    override fun setTrack(index: Int, gainLinear: Float, audible: Boolean) {
        tembra_transport_set_track(index.toUInt(), gainLinear, if (audible) 1u else 0u)
    }

    override fun setMaster(gainLinear: Float) {
        tembra_transport_set_master(gainLinear)
    }

    override fun setMetronome(enabled: Boolean) {
        tembra_transport_set_metronome(if (enabled) 1u else 0u)
    }

    /** The prime reads two seconds of every nearby clip (under 50 ms, but file I/O): off the main thread. */
    override suspend fun play(): PlayResult {
        val code = withContext(Dispatchers.IO) { tembra_transport_play() }
        return if (code == 0) PlayResult.Started else PlayResult.Failed(code)
    }

    override fun pause() {
        tembra_transport_pause()
    }

    override suspend fun seek(beats: Beats) {
        withContext(Dispatchers.IO) { tembra_transport_seek_beats(beats.value) }
    }

    override fun readTransport(into: TransportSnapshot) {
        tembra_transport_read(transport.ptr)
        into.playing = transport.playing != 0u
        into.positionFrames = transport.position_frames
        into.positionBeats = transport.position_beats
        into.audiblePositionSeconds = transport.audible_seconds
        into.blockTimeNanos = transport.block_time_ns
        into.latencyNanos = transport.latency_ns
        into.sampleRate = transport.sample_rate.toInt()
        into.countInBeatsLeft = transport.count_in_beats_left
        into.recording = transport.recording != 0u
    }

    // --- Capture, takes and monitoring (feature 010, contracts/engine-c-abi-v7.md "Kotlin seam") ---------------------

    /** True between a successful [arm] and [disarm] (or the transport closing). */
    @Volatile
    private var armed = false

    /**
     * The session switches to `playAndRecord` before the unit restarts with its input bus (research R3), both
     * latencies are reported, then the engine arms. On the [blocking] dispatcher, never the caller's thread: the
     * restart and the switch take hundreds of milliseconds (T100; the unit is reconfigured here, not primed).
     */
    override suspend fun arm(maxChannels: Int): ArmResult = withContext(blocking) {
        if (audioState != AudioState.Transport) return@withContext ArmResult.NotTransport
        IosAudioSession.activateForRecording()
        reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos())
        tembra_capture_set_input_latency_ns(IosAudioSession.inputLatencyNanos())
        // The route bounds the client format (T098): the unit's own hardware query is a 2-channel default on the first arm.
        captureConfig.max_channels = inputChannelLimit(maxChannels, IosAudioSession.inputChannels()).toUInt()
        captureConfig.reserved = 0u
        val code = tembra_transport_arm(captureConfig.ptr)
        if (code != 0) {
            IosAudioSession.activateForPlayback()
            return@withContext if (code == STATE_REFUSED.code) ArmResult.NotTransport else ArmResult.InputUnavailable
        }
        // The render callback publishes the armed snapshot on its next block: wait for it rather than report a rate of 0.
        repeat(ARM_SETTLE_MILLIS / ARM_POLL_MILLIS) {
            tembra_capture_read(capture.ptr)
            if (capture.armed.toInt() != 0 && capture.sample_rate.toInt() > 0) {
                armed = true
                return@withContext ArmResult.Armed(channels = capture.input_channels.toInt().coerceAtLeast(1), sampleRate = capture.sample_rate.toInt())
            }
            delay(ARM_POLL_MILLIS.toLong())
        }
        tembra_transport_disarm()
        IosAudioSession.activateForPlayback()
        ArmResult.InputUnavailable
    }

    /** The unit restarts without its input bus and the session returns to `playback`, on the [blocking] dispatcher (T100). */
    override suspend fun disarm() = withContext(blocking) {
        tembra_transport_disarm()
        if (armed && audioState == AudioState.Transport) {
            IosAudioSession.activateForPlayback()
            reportOutputLatencyNanos(IosAudioSession.outputLatencyNanos())
        }
        armed = false
    }

    override fun setMonitor(enabled: Boolean, trackIndex: Int) {
        tembra_transport_set_monitor(if (enabled) 1u else 0u, trackIndex.toUInt())
    }

    /** The staging file is created and the position primed (file I/O): off the main thread. */
    override suspend fun recordStart(config: RecordConfig): RecordStartResult = withContext(Dispatchers.IO) {
        memScoped {
            val record = alloc<tembra_record_config>()
            record.wav_path = config.wavPath.cstr.ptr
            record.track_index = config.trackIndex.toUInt()
            record.count_in_beats = config.countInBeats.toUInt()
            record.max_frames = config.maxFrames
            record.min_free_bytes = config.minFreeBytes.toULong()
            when (val code = tembra_transport_record_start(record.ptr)) {
                0 -> RecordStartResult.Started
                IO_ERROR -> RecordStartResult.FileFailed
                else -> {
                    tembra_capture_read(capture.ptr)
                    if (code == STATE_REFUSED.code && capture.armed != 0u) RecordStartResult.TakeRunning else RecordStartResult.NotArmed
                }
            }
        }
    }

    override fun recordCancel() {
        tembra_transport_record_cancel()
    }

    /** The drain and the header finalisation block for up to a second: off the main thread. */
    override suspend fun recordStop(): TakeInfo = withContext(Dispatchers.IO) {
        memScoped {
            val info = alloc<tembra_take_info>()
            if (tembra_transport_record_stop(info.ptr) != 0) {
                TakeInfo(startFrames = 0, startBeats = 0.0, frames = 0, channels = 0, sampleRate = 0, alignmentFrames = 0, stopReason = TakeStopReason.User)
            } else {
                TakeInfo(
                    startFrames = info.start_frames, startBeats = info.start_beats, frames = info.frames, channels = info.channels.toInt(),
                    sampleRate = info.sample_rate.toInt(), alignmentFrames = info.alignment_frames, stopReason = TakeStopReasonCodes.fromNative(info.stop_reason.toInt()),
                )
            }
        }
    }

    override fun readCapture(into: CaptureSnapshot) {
        tembra_capture_read(capture.ptr)
        into.armed = capture.armed != 0u
        into.recording = capture.recording != 0u
        into.preRoll = capture.pre_roll != 0u
        into.monitoring = capture.monitoring != 0u
        into.inputChannels = capture.input_channels.toInt()
        into.sampleRate = capture.sample_rate.toInt()
        into.peak[0] = capture.peak[0]
        into.peak[1] = capture.peak[1]
        into.clipped = capture.clipped != 0u
        into.framesCaptured = capture.frames_captured
        into.takeStartFrames = capture.take_start_frames
        into.alignmentFrames = capture.alignment_frames
        into.inputLatencyNanos = capture.input_latency_ns
        into.outputLatencyNanos = capture.output_latency_ns
        into.stopReason = TakeStopReasonCodes.fromNative(capture.stop_reason.toInt())
        into.inputOverruns = capture.input_overruns.toInt()
        into.backlogFrames = capture.backlog_frames.toInt()
    }

    override fun readPeaks(fromIndex: Long, into: PeakBuckets): Long {
        val wanted = minOf(into.capacity, PeakBuckets.DEFAULT_CAPACITY)
        val count = tembra_capture_read_peaks(fromIndex.toULong(), peakBuckets, wanted.toUInt(), peakNext.ptr).toInt()
        for (i in 0 until count) {
            val bucket = peakBuckets[i]
            into.min[i * 2] = bucket.min[0]
            into.min[i * 2 + 1] = bucket.min[1]
            into.max[i * 2] = bucket.max[0]
            into.max[i * 2 + 1] = bucket.max[1]
        }
        into.count = count
        into.nextIndex = peakNext.value.toLong()
        into.firstIndex = into.nextIndex - count
        return into.nextIndex
    }

    override fun resetClip() {
        tembra_capture_reset_clip()
    }

    override fun freeBytes(path: String): Long = tembra_storage_free_bytes(path)

    override suspend fun inspect(path: String): AudioInfo? = withContext(Dispatchers.IO) {
        memScoped {
            val info = alloc<tembra_audio_info>()
            if (tembra_audio_inspect(path, info.ptr) != 0) return@memScoped null
            val format = AudioFormat.fromNative(info.format.toInt()) ?: return@memScoped null
            AudioInfo(format, channels = info.channels.toInt(), sampleRate = info.sample_rate.toInt(), frames = info.frames)
        }
    }

    override suspend fun buildOverview(audioPath: String, overviewPath: String): Boolean = withContext(Dispatchers.IO) {
        tembra_audio_overview_build(audioPath, overviewPath) == 0
    }

    private fun beatUnitOf(unit: BeatUnit): UInt = when (unit) {
        BeatUnit.Quarter -> 4u
        BeatUnit.Eighth -> 8u
    }

    private companion object {
        /** `TEMBRA_ERR_STATE`: what the engine answers when it is not in a state that allows the call. */
        val STATE_REFUSED = AudioResult.Error(-1)

        /** `TEMBRA_ERR_IO`: the staging file could not be created. */
        const val IO_ERROR = -6
        const val PREVIEW_CHECK_INTERVAL_NANOS = 100_000_000L

        /** 3 s: a custom sound is kept up to 2 s (research R4), so a 1 s bound would leave its session active. */
        const val PREVIEW_CHECKS = 30
    }
}

private const val GRID_LEVELS = 32

/** How long [NativeAudioEngine.arm] waits for the render callback to publish the armed snapshot before giving the input back. */
private const val ARM_SETTLE_MILLIS = 2_000
private const val ARM_POLL_MILLIS = 10
