package audio.tembra.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.engine.perf.AudioHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/**
 * JNI facade over `libtembra_engine.so`. Tones and the preview click hold transient audio focus so other apps duck
 * (research R7); the running metronome holds full focus and listens for headphones being unplugged (feature 004 R6);
 * capture requests nothing (Android gives the microphone to the foreground app). The project transport (feature 009)
 * holds full focus like the metronome and listens for headphones being unplugged; a focus loss reaches the session as
 * [AudioEvent.Interrupted] and never resumes playback by itself. Nothing here runs on an audio thread.
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

    /** Reused by every [readPitch]: [state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch, synthetic, inputLevelDb, gateOpen]. */
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

    override fun setNoiseGate(openDb: Float?): AudioResult = AudioResult.fromNative(nativeTunerSetGate(openDb != null, openDb ?: 0f))

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
        into.inputLevelDb = reading[7]
        into.gateOpen = reading[8] != 0f
    }

    override fun startTone(frequencyHz: Double, durationMs: Int): AudioResult = sound { nativeToneStart(frequencyHz, durationMs) }

    /**
     * Tones and samples: from Idle they take transient focus; over a sounding tone the engine re-arms it and the focus
     * it already holds stays; while listening or the metronome runs the engine refuses, and nothing is touched.
     */
    private inline fun sound(start: () -> Int): AudioResult {
        when (audioState) {
            AudioState.Listening, AudioState.Metronome, AudioState.Transport -> return STATE_REFUSED
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
        // A running metronome or transport owns the focus; otherwise release what a tone, sample or preview left behind.
        if (audioState == AudioState.Metronome || audioState == AudioState.Transport) return
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
                        AudioState.Listening, AudioState.Metronome, AudioState.Transport -> previewRelease = null
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

    /** Reused by every [audioHealth]: [callbacks, lastUs, maxUs, overBudget, xruns, realtime, avgUs, starvation, clipsActive]. */
    private val healthFields = LongArray(HEALTH_FIELDS)

    override fun audioHealth(): AudioHealth? {
        if (nativeAudioHealth(healthFields) != 0) return null
        return AudioHealth(
            callbacks = healthFields[0],
            lastMicros = healthFields[1].toInt(),
            maxMicros = healthFields[2].toInt(),
            overBudget = healthFields[3].toInt(),
            xruns = healthFields[4].toInt(),
            realtime = healthFields[5] != 0L,
            avgMicros = healthFields[6].toInt(),
            starvation = healthFields[7].toInt(),
            clipsActive = healthFields[8].toInt(),
        )
    }

    override val events: Flow<AudioEvent> = merge(focus.events, routeEvents)

    // --- Transport (feature 009, contracts/timeline-audio-kotlin.md) -------------------------------------------------

    private val arrangement = ArrangementBuffer()

    /** Reused by every [readTransport]: [playing, positionFrames, blockTimeNs, latencyNs, sampleRate] and [positionBeats, audibleSeconds]. */
    private val transportLongs = LongArray(TRANSPORT_LONGS)
    private val transportDoubles = DoubleArray(TRANSPORT_DOUBLES)

    /**
     * From Idle only: full focus (other media pause) and the becoming-noisy receiver, then the engine; a refusal on either
     * side holds nothing. Opening the output is a platform call, so it runs off the main thread.
     */
    override suspend fun openTransport(): TransportOpenResult {
        val state = audioState
        if (state != AudioState.Idle) return TransportOpenResult.Busy(state)
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
        if (!focus.request(FocusType.Gain)) return TransportOpenResult.DeviceFailed
        becomingNoisy.register()
        val code = withContext(Dispatchers.IO) { nativeTransportOpen() }
        if (code != 0) {
            becomingNoisy.unregister()
            focus.abandon()
            return if (code == STATE_REFUSED.code) TransportOpenResult.Busy(audioState) else TransportOpenResult.DeviceFailed
        }
        return TransportOpenResult.Opened
    }

    override fun closeTransport() {
        nativeTransportClose()
        becomingNoisy.unregister()
        // A tone or preview still sounding owns the focus (its own stop or the preview check releases it).
        if (audioState == AudioState.Tone) return
        synchronized(previewLock) { cancelPreviewReleaseLocked() }
        focus.abandon()
    }

    override suspend fun openClipSource(path: String): ClipSourceResult = withContext(Dispatchers.IO) {
        // Allocated per call: a source opens rarely and the MP3 seek table can take a while, so a shared buffer could race.
        val ints = IntArray(CLIP_INFO_INTS)
        val longs = LongArray(CLIP_INFO_LONGS)
        val handle = nativeClipSourceOpen(path, ints, longs)
        val format = AudioFormat.fromNative(ints[2])
        if (handle < 0 || format == null) {
            ClipSourceResult.Undecodable
        } else {
            ClipSourceResult.Opened(ClipSourceHandle(handle), AudioInfo(format, channels = ints[0], sampleRate = ints[1], frames = longs[0]))
        }
    }

    override fun closeClipSource(handle: ClipSourceHandle) {
        nativeClipSourceClose(handle.id)
    }

    override fun setTempo(tempo: Tempo, signature: TimeSignature) {
        nativeTransportSetTempo(tempo.bpm, signature.beats, beatUnitOf(signature.unit))
    }

    override fun setArrangement(snapshot: ArrangementSnapshot) {
        arrangement.fill(snapshot)
        nativeTransportSetArrangement(arrangement.buffer)
    }

    override fun setTrack(index: Int, gainLinear: Float, audible: Boolean) {
        nativeTransportSetTrack(index, gainLinear, audible)
    }

    override fun setMaster(gainLinear: Float) {
        nativeTransportSetMaster(gainLinear)
    }

    override fun setMetronome(enabled: Boolean) {
        nativeTransportSetMetronome(enabled)
    }

    /** The prime reads two seconds of every nearby clip (under 50 ms, but file I/O): off the main thread. */
    override suspend fun play(): PlayResult {
        val code = withContext(Dispatchers.IO) { nativeTransportPlay() }
        return if (code == 0) PlayResult.Started else PlayResult.Failed(code)
    }

    override fun pause() {
        nativeTransportPause()
    }

    override suspend fun seek(beats: Beats) {
        withContext(Dispatchers.IO) { nativeTransportSeekBeats(beats.value) }
    }

    override fun readTransport(into: TransportSnapshot) {
        nativeTransportRead(transportLongs, transportDoubles)
        into.playing = transportLongs[0] != 0L
        into.positionFrames = transportLongs[1]
        into.blockTimeNanos = transportLongs[2]
        into.latencyNanos = transportLongs[3]
        into.sampleRate = transportLongs[4].toInt()
        into.positionBeats = transportDoubles[0]
        into.audiblePositionSeconds = transportDoubles[1]
    }

    override suspend fun inspect(path: String): AudioInfo? = withContext(Dispatchers.IO) {
        val ints = IntArray(CLIP_INFO_INTS)
        val longs = LongArray(CLIP_INFO_LONGS)
        if (nativeAudioInspect(path, ints, longs) != 0) return@withContext null
        val format = AudioFormat.fromNative(ints[0]) ?: return@withContext null
        AudioInfo(format, channels = ints[1], sampleRate = ints[2], frames = longs[0])
    }

    override suspend fun buildOverview(audioPath: String, overviewPath: String): Boolean = withContext(Dispatchers.IO) {
        nativeAudioOverviewBuild(audioPath, overviewPath) == 0
    }

    private fun beatUnitOf(unit: BeatUnit): Int = when (unit) {
        BeatUnit.Quarter -> 4
        BeatUnit.Eighth -> 8
    }

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
    private external fun nativeTunerSetGate(enabled: Boolean, openDb: Float): Int
    private external fun nativeTunerRead(out: FloatArray): Long
    private external fun nativeToneStart(hz: Double, durationMs: Int): Int
    private external fun nativeSampleLoadWav(bytes: ByteArray): Int
    private external fun nativeSamplePlay(handle: Int, loop: Boolean): Int
    private external fun nativeSampleUnload(handle: Int)
    private external fun nativeToneStop()
    private external fun nativeAudioState(): Int
    private external fun nativeAudioHealth(out: LongArray): Int
    private external fun nativeMetronomeSetGrid(bpm: Double, ticksPerBeat: Int, ticksPerBar: Int, levels: ByteArray): Int
    private external fun nativeMetronomeSelectSound(sound: Int): Int
    private external fun nativeMetronomeLoadCustomSound(path: String, info: IntArray, ms: FloatArray): Int
    private external fun nativeMetronomeUnloadCustomSound()
    private external fun nativeMetronomeStart(): Int
    private external fun nativeMetronomeStop()
    private external fun nativeMetronomePreview(level: Int): Int
    private external fun nativeMetronomeSetOutputLatencyNs(nanos: Long)
    private external fun nativeMetronomeRead(out: LongArray)
    private external fun nativeTransportOpen(): Int
    private external fun nativeTransportClose()
    private external fun nativeTransportSetTempo(bpm: Int, beatsPerBar: Int, beatUnit: Int): Int
    private external fun nativeTransportSetArrangement(buffer: java.nio.ByteBuffer): Int
    private external fun nativeTransportSetTrack(index: Int, gain: Float, audible: Boolean): Int
    private external fun nativeTransportSetMaster(gain: Float): Int
    private external fun nativeTransportSetMetronome(enabled: Boolean): Int
    private external fun nativeTransportPlay(): Int
    private external fun nativeTransportPause(): Int
    private external fun nativeTransportSeekBeats(beats: Double): Int
    private external fun nativeTransportRead(longs: LongArray, doubles: DoubleArray)
    private external fun nativeClipSourceOpen(path: String, ints: IntArray, longs: LongArray): Int
    private external fun nativeClipSourceClose(handle: Int)
    private external fun nativeAudioInspect(path: String, ints: IntArray, longs: LongArray): Int
    private external fun nativeAudioOverviewBuild(path: String, outPath: String): Int

    private companion object {
        const val READING_FIELDS = 9
        const val TICK_FIELDS = 7
        const val CUSTOM_INFO_FIELDS = 4
        const val CUSTOM_MS_FIELDS = 2
        const val HEALTH_FIELDS = 9
        const val TRANSPORT_LONGS = 5
        const val TRANSPORT_DOUBLES = 2
        const val CLIP_INFO_INTS = 3
        const val CLIP_INFO_LONGS = 1
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
