package audio.tembra.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.recording.TakeStopReason
import audio.tembra.engine.perf.AudioHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.delay
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

    /** The device-change watcher registered while the transport is open (feature 010); internal for device tests. */
    internal val routeWatcher = AndroidAudioRouteWatcher(context) { routeEvents.tryEmit(it) }

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

    /** Reused by every [audioHealth]: [callbacks, lastUs, maxUs, overBudget, xruns, realtime, avgUs, starvation, clipsActive, inputOverruns, recordBacklog, inputXruns]. */
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
            inputOverruns = healthFields[9].toInt(),
            recordBacklog = healthFields[10].toInt(),
            inputXruns = healthFields[11].toInt(),
        )
    }

    override val events: Flow<AudioEvent> = merge(focus.events, routeEvents)

    // --- Transport (feature 009, contracts/timeline-audio-kotlin.md) -------------------------------------------------

    private val arrangement = ArrangementBuffer()

    /** Reused by every [readTransport]: [playing, positionFrames, blockTimeNs, latencyNs, sampleRate, countInBeatsLeft, recording] and [positionBeats, audibleSeconds]. */
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
        routeWatcher.register()
        return TransportOpenResult.Opened
    }

    override fun closeTransport() {
        nativeTransportClose()
        routeWatcher.unregister()
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
        into.countInBeatsLeft = transportLongs[5].toInt()
        into.recording = transportLongs[6] != 0L
        into.positionBeats = transportDoubles[0]
        into.audiblePositionSeconds = transportDoubles[1]
    }

    // --- Capture, takes and monitoring (feature 010, contracts/engine-c-abi-v7.md "Kotlin seam") ---------------------

    /** Reused by every [readCapture]: the ints, floats and longs of `tembra_capture_snapshot`. */
    private val captureInts = IntArray(CAPTURE_INTS)
    private val captureFloats = FloatArray(CAPTURE_FLOATS)
    private val captureLongs = LongArray(CAPTURE_LONGS)
    private val peakBuffer = PeakBucketBuffer(PeakBuckets.DEFAULT_CAPACITY)
    private val peakNext = LongArray(1)

    /**
     * Opening the second Oboe stream is a platform call (research R2): off the main thread. The callback publishes the
     * armed snapshot (channels, rate) on its next block, so the result waits for it, up to [ARM_SETTLE_MILLIS]; an
     * input that never reaches the callback (no microphone path, a stream the policy silenced) is closed again and
     * reported unavailable rather than armed at a rate of 0.
     */
    override suspend fun arm(maxChannels: Int): ArmResult {
        val code = withContext(Dispatchers.IO) { nativeTransportArm(maxChannels) }
        if (code == STATE_REFUSED.code) return ArmResult.NotTransport
        if (code != 0) return ArmResult.InputUnavailable
        repeat(ARM_SETTLE_MILLIS / ARM_POLL_MILLIS) {
            nativeCaptureRead(captureLongs, captureFloats, captureInts)
            if (captureInts[0] != 0 && captureInts[5] > 0) return ArmResult.Armed(channels = captureInts[4].coerceAtLeast(1), sampleRate = captureInts[5])
            delay(ARM_POLL_MILLIS.toLong())
        }
        nativeTransportDisarm()
        return ArmResult.InputUnavailable
    }

    override suspend fun disarm() {
        nativeTransportDisarm()
    }

    override fun setMonitor(enabled: Boolean, trackIndex: Int) {
        nativeTransportSetMonitor(enabled, trackIndex)
    }

    /** The staging file is created and the position primed (file I/O): off the main thread. */
    override suspend fun recordStart(config: RecordConfig): RecordStartResult {
        val code = withContext(Dispatchers.IO) {
            nativeTransportRecordStart(config.wavPath, config.trackIndex, config.countInBeats, config.maxFrames, config.minFreeBytes)
        }
        return when (code) {
            0 -> RecordStartResult.Started
            IO_ERROR -> RecordStartResult.FileFailed
            else -> if (nativeCaptureArmed()) RecordStartResult.TakeRunning else RecordStartResult.NotArmed
        }
    }

    private fun nativeCaptureArmed(): Boolean {
        nativeCaptureRead(captureLongs, captureFloats, captureInts)
        return captureInts[0] != 0
    }

    override fun recordCancel() {
        nativeTransportRecordCancel()
    }

    /** The drain and the header finalisation block for up to a second: off the main thread. */
    override suspend fun recordStop(): TakeInfo = withContext(Dispatchers.IO) {
        // Allocated per call: a take stops rarely and never from two threads at once.
        val longs = LongArray(TAKE_LONGS)
        val doubles = DoubleArray(TAKE_DOUBLES)
        val ints = IntArray(TAKE_INTS)
        val code = nativeTransportRecordStop(longs, doubles, ints)
        if (code != 0) {
            TakeInfo(startFrames = 0, startBeats = 0.0, frames = 0, channels = 0, sampleRate = 0, alignmentFrames = 0, stopReason = TakeStopReason.User)
        } else {
            TakeInfo(
                startFrames = longs[0], startBeats = doubles[0], frames = longs[1], channels = ints[0], sampleRate = ints[1],
                alignmentFrames = longs[2], stopReason = TakeStopReasonCodes.fromNative(ints[2]),
            )
        }
    }

    override fun readCapture(into: CaptureSnapshot) {
        nativeCaptureRead(captureLongs, captureFloats, captureInts)
        into.armed = captureInts[0] != 0
        into.recording = captureInts[1] != 0
        into.preRoll = captureInts[2] != 0
        into.monitoring = captureInts[3] != 0
        into.inputChannels = captureInts[4]
        into.sampleRate = captureInts[5]
        into.clipped = captureInts[6] != 0
        into.stopReason = TakeStopReasonCodes.fromNative(captureInts[7])
        into.inputOverruns = captureInts[8]
        into.backlogFrames = captureInts[9]
        into.peak[0] = captureFloats[0]
        into.peak[1] = captureFloats[1]
        into.framesCaptured = captureLongs[0]
        into.takeStartFrames = captureLongs[1]
        into.alignmentFrames = captureLongs[2]
        into.inputLatencyNanos = captureLongs[3]
        into.outputLatencyNanos = captureLongs[4]
    }

    override fun readPeaks(fromIndex: Long, into: PeakBuckets): Long {
        val count = nativeCaptureReadPeaks(fromIndex, peakBuffer.buffer, minOf(into.capacity, peakBuffer.capacity), peakNext)
        peakBuffer.copyInto(into, count)
        into.nextIndex = peakNext[0]
        into.firstIndex = peakNext[0] - count
        return peakNext[0]
    }

    override fun resetClip() {
        nativeCaptureResetClip()
    }

    override fun freeBytes(path: String): Long = nativeStorageFreeBytes(path)

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
    private external fun nativeTransportArm(maxChannels: Int): Int
    private external fun nativeTransportDisarm()
    private external fun nativeTransportSetMonitor(enabled: Boolean, trackIndex: Int): Int
    private external fun nativeCaptureSetInputLatencyNs(nanos: Long)
    private external fun nativeTransportRecordStart(path: String, trackIndex: Int, countInBeats: Int, maxFrames: Long, minFreeBytes: Long): Int
    private external fun nativeTransportRecordCancel(): Int
    private external fun nativeTransportRecordStop(longs: LongArray, doubles: DoubleArray, ints: IntArray): Int
    private external fun nativeCaptureRead(longs: LongArray, floats: FloatArray, ints: IntArray)
    private external fun nativeCaptureResetClip()
    private external fun nativeCaptureReadPeaks(fromIndex: Long, buffer: java.nio.ByteBuffer, maxCount: Int, next: LongArray): Int
    private external fun nativeStorageFreeBytes(path: String): Long

    private companion object {
        const val READING_FIELDS = 9
        const val TICK_FIELDS = 7
        const val CUSTOM_INFO_FIELDS = 4
        const val CUSTOM_MS_FIELDS = 2
        const val HEALTH_FIELDS = 12
        const val TRANSPORT_LONGS = 7
        const val TRANSPORT_DOUBLES = 2
        const val CAPTURE_INTS = 10
        const val CAPTURE_FLOATS = 2
        const val CAPTURE_LONGS = 5
        const val TAKE_LONGS = 3
        const val TAKE_DOUBLES = 1
        const val TAKE_INTS = 3
        const val ARM_SETTLE_MILLIS = 2_000
        const val ARM_POLL_MILLIS = 10

        /** `TEMBRA_ERR_IO`: the staging file could not be created. */
        const val IO_ERROR = -6
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
