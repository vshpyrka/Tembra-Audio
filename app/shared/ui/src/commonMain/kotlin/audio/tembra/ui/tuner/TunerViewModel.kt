package audio.tembra.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.data.tuner.TunerPreferences
import audio.tembra.data.tuner.TunerPreferencesSnapshot
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.PitchSnapshot
import audio.tembra.engine.SampleHandle
import audio.tembra.engine.TestSignal
import audio.tembra.engine.TunerAudio
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Tuner destination's ViewModel (contracts/tuner-screen.md). It owns the microphone lease through
 * [ListeningPolicy]: every input change recomputes what the audio should do and applies the transition (stop the
 * current stream, then start the desired one) on [audioDispatcher], one transition at a time. A 10 Hz poll mirrors
 * the engine's state and reading for semantics and notices when the engine stopped by itself (watchdog, one-shot
 * tone); frame-rate telemetry goes through [telemetry] straight into the meter's draw phase (Constitution II).
 * Selector choices persist through [TunerPreferences]. Nothing is launched from the constructor: the first
 * [screenVisible] call is the launch site.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class TunerViewModel(
    private val audio: TunerAudio,
    private val permission: MicrophonePermission,
    private val preferences: TunerPreferences,
    private val sounds: ReferenceSoundRepository,
    /** Where recordings are decoded (analysis M6); an optional binding, so the graph provides nothing for it. */
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Where the engine's streams start and stop. Opening the microphone blocks while the platform brings the device
     * up (about 70 ms on a Pixel 6 Pro, 130 ms on an iPhone 14), which on the main thread dropped the first frames of
     * the screen that asked for it. One thread, so stops and starts keep their order; an optional binding.
     */
    private val audioDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    /** Stable handle for draw-phase reads: fills a snapshot without allocating. */
    val telemetry: PitchTelemetry = PitchTelemetry { audio.readPitch(it) }

    private var screenVisible = false
    private var foreground = false
    private var requestedThisVisibility = false

    /** What the last finished transition reached, as this (main) thread knows it. */
    private var current: DesiredAudio = DesiredAudio.Idle

    /** True while a transition runs on [audioDispatcher]; inputs that change meanwhile are applied when it returns. */
    private var transitioning = false
    private var pendingApply = false
    private var pendingRestart = Restart.None

    /** What the engine runs for this ViewModel; read and written only on [audioDispatcher]. */
    private var engineAudio: DesiredAudio = DesiredAudio.Idle
    private var startJob: Job? = null
    private var pollJob: Job? = null

    /** The debug launch value of the noise gate, if one was handed over ([useNoiseGate]). */
    private var launchGate: NoiseGate? = null
    private var auditionJob: Job? = null
    private val snapshot = PitchSnapshot()
    private val sampleHandles = mutableMapOf<String, SampleHandle>()

    // --- Lifecycle intents ---------------------------------------------------------------------------------------

    /** Visibility of the Tuner entry; the first time it shows, the ViewModel loads its preferences and starts. */
    fun screenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) {
            start()
        } else {
            requestedThisVisibility = false
            clearAudition()
        }
        applyAudio()
    }

    /** `ON_START` / `ON_STOP` (research R7); a tone stopped by backgrounding never restarts (spec FR-020). */
    fun foreground(started: Boolean) {
        foreground = started
        if (!started) clearAudition()
        applyAudio()
    }

    fun onResumed() {
        permission.refresh()
    }

    fun useTestSignal(signal: TestSignal?) {
        if (_uiState.value.testSignal == signal) return
        _uiState.update { it.copy(testSignal = signal) }
        applyAudio()
    }

    /**
     * The debug launch value of the noise gate (feature 006, data-model.md §8): it stands in for the stored gate for
     * this launch and is never written; a later move of the handle is remembered as usual. The screen hands it over
     * before it reports itself visible, so it is in force from the first sound. The same value handed over again
     * (the screen's effect ran anew) changes nothing.
     */
    fun useNoiseGate(gate: NoiseGate?) {
        if (gate == null || gate == launchGate) return
        launchGate = gate
        if (_uiState.value.loaded) setNoiseGate(gate)
    }

    /**
     * The one launch site: the stored preferences and the current permission first, then the permission and the
     * platform events for the rest of the ViewModel's life. [applyAudio] does nothing before [TunerUiState.loaded],
     * so intents that arrive earlier only change state, and the first policy decision never sees the placeholder
     * permission of an empty state.
     */
    private fun start() {
        if (startJob != null) return
        startJob = viewModelScope.launch {
            val stored = preferences.snapshot.first()
            // From the stored preferences to the first content state and the engine's first command: the tuner.load
            // section of the performance catalogue (feature 007); synchronous, so one interval on this thread.
            PerfTrace.section(PerfSection.TUNER_LOAD) {
                val presets = TuningPresets.forInstrument(stored.instrument, stored.guitarStrings)
                val preset = presets.firstOrNull { it.id == stored.presetId } ?: presets.first()
                val gate = launchGate ?: stored.noiseGate
                _uiState.update {
                    it.copy(
                        loaded = true,
                        instrument = stored.instrument,
                        guitarStrings = stored.guitarStrings,
                        preset = preset,
                        presets = presets,
                        mode = stored.mode,
                        loopTone = stored.loopTone,
                        noiseGate = gate,
                        microphone = permission.status.value,
                    )
                }
                // A remembered gate is in the engine before listening can start, so the first hop is gated (spec FR-015).
                // The engine starts with no gate, so "off" needs no call.
                if (!gate.isOff) audio.setNoiseGate(gate.levelDb?.toFloat())
                applyAudio()
            }
            launch {
                permission.status.collect { status ->
                    // The seeded value is replayed first; only a change is a new input for the policy.
                    if (_uiState.value.microphone == status) return@collect
                    _uiState.update { it.copy(microphone = status) }
                    applyAudio()
                }
            }
            launch { audio.events.collect(::onEvent) }
        }
    }

    // --- Selector intents (spec FR-004, FR-005) --------------------------------------------------------------------

    fun selectInstrument(instrument: Instrument) {
        val state = _uiState.value
        if (state.instrument == instrument) return
        val presets = TuningPresets.forInstrument(instrument, state.guitarStrings)
        val preset = presets.first()
        cancelPendingAudition()
        unloadSamples()
        _uiState.update { it.copy(instrument = instrument, presets = presets, preset = preset, audition = null) }
        persist { it.copy(instrument = instrument, presetId = preset.id) }
        applyAudio()
    }

    fun selectGuitarStrings(count: GuitarStringCount) {
        val state = _uiState.value
        if (state.guitarStrings == count) return
        val presets = TuningPresets.forInstrument(state.instrument, count)
        val preset = presets.first()
        cancelPendingAudition()
        _uiState.update { it.copy(guitarStrings = count, presets = presets, preset = preset, audition = null) }
        persist { it.copy(guitarStrings = count, presetId = preset.id) }
        applyAudio()
    }

    fun selectPreset(id: String) {
        val state = _uiState.value
        val preset = state.presets.firstOrNull { it.id == id } ?: return
        if (state.preset == preset) return
        cancelPendingAudition()
        _uiState.update { it.copy(preset = preset, audition = null) }
        persist { it.copy(presetId = preset.id) }
        applyAudio()
    }

    fun selectMode(mode: TunerMode) {
        if (_uiState.value.mode == mode) return
        // The tuner.modeSwitch section (feature 007): the intent's own work, not the recomposition it causes.
        PerfTrace.section(PerfSection.TUNER_MODE_SWITCH) {
            cancelPendingAudition()
            _uiState.update { it.copy(mode = mode, audition = null) }
            persist { it.copy(mode = mode) }
            applyAudio()
        }
    }

    /** Persists the toggle and re-arms a sounding tone without restarting it (spec FR-019). */
    fun setLoopTone(on: Boolean) {
        if (_uiState.value.loopTone == on) return
        _uiState.update { state -> state.copy(loopTone = on, audition = state.audition?.copy(loop = on)) }
        persist { it.copy(loopTone = on) }
        applyAudio()
    }

    // --- Noise gate (feature 006, spec FR-003) ------------------------------------------------------------------------

    /**
     * Every step the noise gate's handle reaches. The engine takes the level at once and keeps it across stops and
     * starts, so listening is never restarted by a move; nothing is written until [noiseGateSettled].
     */
    fun setNoiseGate(gate: NoiseGate) {
        if (_uiState.value.noiseGate == gate) return
        _uiState.update { it.copy(noiseGate = gate) }
        audio.setNoiseGate(gate.levelDb?.toFloat())
    }

    /** The move of the handle has ended: the level is remembered, once per move (spec FR-014). */
    fun noiseGateSettled() {
        val gate = _uiState.value.noiseGate
        persist { it.copy(noiseGate = gate) }
    }

    // --- Audition intents (spec FR-018 to FR-020) ------------------------------------------------------------------

    /**
     * Sounds the string at [stringIndex]. Resolving and decoding its sound is owned by [auditionJob]: a newer
     * audition, a stop, a selector change or leaving the screen cancels it, so a slow decode can never sound a
     * string the user no longer asked for.
     */
    fun audition(stringIndex: Int) {
        val state = _uiState.value
        val string = state.strings.getOrNull(stringIndex) ?: return
        cancelPendingAudition()
        auditionJob = viewModelScope.launch {
            val sound = sounds.resolve(state.instrument, string.note)
            if (sound is ReferenceSound.Recording && sampleHandles[sound.resourceName] == null) {
                // The decode finishes and is cached even if this audition is cancelled meanwhile, so no handle is lost.
                withContext(NonCancellable) {
                    val handle = withContext(decodeDispatcher) { audio.loadSample(sound.bytes) }
                    if (handle != null) sampleHandles[sound.resourceName] = handle
                }
            }
            ensureActive()
            _uiState.update { it.copy(audition = AuditionState(stringIndex, it.loopTone, sound)) }
            applyAudio()
        }
    }

    fun stopAudition() {
        clearAudition()
        applyAudio()
    }

    // --- Permission (spec FR-014, FR-015) --------------------------------------------------------------------------

    fun requestMicrophone() {
        when (_uiState.value.microphone) {
            MicrophonePermissionStatus.NotDetermined,
            MicrophonePermissionStatus.DeniedCanAsk,
            -> viewModelScope.launch { permission.request() }
            MicrophonePermissionStatus.DeniedOpenSettings -> permission.openSystemSettings()
            MicrophonePermissionStatus.Granted -> Unit
        }
    }

    // --- Applying the policy ----------------------------------------------------------------------------------------

    /**
     * Recomputes the desired audio and, when it differs from [current] (or [restart] says the engine's own record is
     * stale), moves the engine there on [audioDispatcher]. The caller's thread never waits for a stream to open; the
     * state and the poll follow when the transition returns.
     */
    private fun applyAudio(restart: Restart = Restart.None) {
        val state = _uiState.value
        if (!state.loaded) return
        requestPermissionIfNeeded(state)
        if (transitioning) {
            pendingApply = true
            if (restart != Restart.None) pendingRestart = restart
            return
        }
        val inputs = PolicyInputs(state.mode, screenVisible, foreground, state.microphone, state.audition, state.interrupted, state.testSignal)
        val desired = ListeningPolicy.desired(inputs)
        if (desired == current && restart == Restart.None) {
            _uiState.update { it.copy(audio = audio.audioState) }
            updatePolling()
            return
        }
        // Read on this thread, where the decodes store them.
        val sample = ((desired as? DesiredAudio.Tone)?.sound as? ReferenceSound.Recording)?.let { sampleHandles[it.resourceName] }
        transitioning = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val reached = withContext(audioDispatcher) { switchEngine(desired, sample, restart) }
            current = reached
            transitioning = false
            if (reached == DesiredAudio.Idle && desired is DesiredAudio.Tone) _uiState.update { it.copy(audition = null) }
            _uiState.update { it.copy(audio = audio.audioState) }
            updatePolling()
            if (pendingApply) {
                val again = pendingRestart
                pendingApply = false
                pendingRestart = Restart.None
                applyAudio(again)
            }
        }
    }

    /** On [audioDispatcher]: moves the engine from [engineAudio] to [desired] and returns what it reached. */
    private fun switchEngine(desired: DesiredAudio, sample: SampleHandle?, restart: Restart): DesiredAudio {
        when (restart) {
            Restart.None -> Unit
            // The engine stopped by itself (watchdog, one-shot tone): nothing is left to stop.
            Restart.EngineStopped -> engineAudio = DesiredAudio.Idle
            Restart.StopFirst -> {
                stopAudio(engineAudio)
                engineAudio = DesiredAudio.Idle
            }
        }
        val previous = engineAudio
        val reached = when {
            previous == desired -> desired
            // Same tone, other loop setting: the engine re-arms without a restart (spec FR-019).
            previous is DesiredAudio.Tone && desired is DesiredAudio.Tone && previous.sound == desired.sound &&
                desired.sound is ReferenceSound.Synth -> if (startTone(desired, sample)) desired else DesiredAudio.Idle
            else -> {
                stopAudio(previous)
                startAudio(desired, sample)
            }
        }
        engineAudio = reached
        return reached
    }

    private fun requestPermissionIfNeeded(state: TunerUiState) {
        val wantsMicrophone = state.mode == TunerMode.AutoListen && screenVisible && foreground && state.testSignal == null
        if (wantsMicrophone && state.microphone == MicrophonePermissionStatus.NotDetermined && !requestedThisVisibility) {
            requestedThisVisibility = true
            viewModelScope.launch { permission.request() }
        }
    }

    private fun stopAudio(running: DesiredAudio) {
        when (running) {
            DesiredAudio.Idle -> Unit
            DesiredAudio.Listening, is DesiredAudio.TestListening -> audio.stopListening()
            is DesiredAudio.Tone -> audio.stopTone()
        }
    }

    private fun startAudio(desired: DesiredAudio, sample: SampleHandle?): DesiredAudio = when (desired) {
        DesiredAudio.Idle -> DesiredAudio.Idle
        DesiredAudio.Listening -> if (audio.startListening() == AudioResult.Ok) desired else DesiredAudio.Idle
        is DesiredAudio.TestListening -> if (audio.startTestSignal(desired.signal) == AudioResult.Ok) desired else DesiredAudio.Idle
        is DesiredAudio.Tone -> if (startTone(desired, sample)) desired else DesiredAudio.Idle
    }

    /** True when the engine sounds [tone]; a recording whose decode failed has no [sample] to play. */
    private fun startTone(tone: DesiredAudio.Tone, sample: SampleHandle?): Boolean = when (val sound = tone.sound) {
        is ReferenceSound.Synth -> audio.startTone(sound.frequencyHz, if (tone.loop) 0 else ONE_SHOT_MILLIS) == AudioResult.Ok
        is ReferenceSound.Recording -> sample != null && audio.playSample(sample, tone.loop) == AudioResult.Ok
    }

    private fun updatePolling() {
        if (current != DesiredAudio.Idle) {
            if (pollJob == null) {
                pollJob = viewModelScope.launch {
                    while (isActive) {
                        poll()
                        delay(POLL_MILLIS)
                    }
                }
            }
        } else {
            pollJob?.cancel()
            pollJob = null
            if (_uiState.value.reading != null || !_uiState.value.gateOpen) _uiState.update { it.copy(reading = null, gateOpen = true) }
        }
    }

    /** The 10 Hz mirror of the engine: the reading for semantics and the state; restarts what the engine dropped. */
    private fun poll() {
        audio.readPitch(snapshot)
        val engineState = audio.audioState
        val reading = if (engineState == AudioState.Listening && snapshot.hasPitch) MeterReading.from(snapshot) else null
        // A gate is only ever closed while listening; any other state reads as open.
        val gateOpen = engineState != AudioState.Listening || snapshot.gateOpen
        _uiState.update { it.copy(audio = engineState, reading = reading, gateOpen = gateOpen) }
        // Mid-transition the engine is briefly idle between the stop and the start; only a settled state counts.
        if (!transitioning && engineState == AudioState.Idle && current != DesiredAudio.Idle) {
            val wasTone = current is DesiredAudio.Tone
            current = DesiredAudio.Idle
            if (wasTone) _uiState.update { it.copy(audition = null) }
            applyAudio(Restart.EngineStopped)
        }
    }

    private fun onEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.Interrupted -> {
                cancelPendingAudition()
                _uiState.update { it.copy(interrupted = true, audition = null) }
                applyAudio()
            }
            AudioEvent.InterruptionEnded -> {
                _uiState.update { it.copy(interrupted = false) }
                applyAudio()
            }
            AudioEvent.DeviceLost -> {
                current = DesiredAudio.Idle
                applyAudio(Restart.StopFirst)
            }
            AudioEvent.OutputRouteLost -> {
                cancelPendingAudition()
                _uiState.update { it.copy(audition = null) }
                applyAudio()
            }
            // Feature 010: the recording session's route changes; the tuner's own stream is untouched by them.
            AudioEvent.InputRouteChanged, is AudioEvent.OutputRouteChanged -> Unit
        }
    }

    /** Drops an audition that is still resolving or decoding; a sounding one is untouched. */
    private fun cancelPendingAudition() {
        auditionJob?.cancel()
        auditionJob = null
    }

    private fun clearAudition() {
        cancelPendingAudition()
        if (_uiState.value.audition != null) _uiState.update { it.copy(audition = null) }
    }

    private fun persist(transform: (TunerPreferencesSnapshot) -> TunerPreferencesSnapshot) {
        viewModelScope.launch { preferences.update(transform) }
    }

    private fun unloadSamples() {
        for (handle in sampleHandles.values) audio.unloadSample(handle)
        sampleHandles.clear()
    }

    override fun onCleared() {
        cancelPendingAudition()
        pollJob?.cancel()
        pollJob = null
        current = DesiredAudio.Idle
        // The engine keeps a gate for the life of the process; one this ViewModel set must not outlive it.
        val gateSet = !_uiState.value.noiseGate.isOff
        val samples = sampleHandles.values.toList()
        sampleHandles.clear()
        // The release is the one launch outside viewModelScope, which is already cancelled here: it queues on
        // audioDispatcher behind a transition still in flight, so the engine always ends idle.
        CoroutineScope(audioDispatcher).launch {
            stopAudio(engineAudio)
            engineAudio = DesiredAudio.Idle
            if (gateSet) audio.setNoiseGate(null)
            for (handle in samples) audio.unloadSample(handle)
        }
        super.onCleared()
    }

    /** How a transition treats the engine's own record before it moves on. */
    private enum class Restart {
        None,

        /** The engine stopped by itself; there is nothing to stop. */
        EngineStopped,

        /** The device was lost; stop what ran before starting again. */
        StopFirst,
    }

    private companion object {
        const val ONE_SHOT_MILLIS = 2000
        const val POLL_MILLIS = 100L
    }
}
