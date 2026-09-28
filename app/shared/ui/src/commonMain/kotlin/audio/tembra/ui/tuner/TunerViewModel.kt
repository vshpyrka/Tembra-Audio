package audio.tembra.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
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
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.MicrophonePermissionStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
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
 * current stream, then start the desired one). A 10 Hz poll mirrors the engine's state and reading for semantics
 * and notices when the engine stopped by itself (watchdog, one-shot tone); frame-rate telemetry goes through
 * [telemetry] straight into the meter's draw phase (Constitution II). Selector choices persist through
 * [TunerPreferences]. Nothing is launched from the constructor: the first [screenVisible] call is the launch site.
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    /** Stable handle for draw-phase reads: fills a snapshot without allocating. */
    val telemetry: PitchTelemetry = PitchTelemetry { audio.readPitch(it) }

    private var screenVisible = false
    private var foreground = false
    private var requestedThisVisibility = false
    private var current: DesiredAudio = DesiredAudio.Idle
    private var startJob: Job? = null
    private var pollJob: Job? = null
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
     * The one launch site: the stored preferences and the current permission first, then the permission and the
     * platform events for the rest of the ViewModel's life. [applyAudio] does nothing before [TunerUiState.loaded],
     * so intents that arrive earlier only change state, and the first policy decision never sees the placeholder
     * permission of an empty state.
     */
    private fun start() {
        if (startJob != null) return
        startJob = viewModelScope.launch {
            val stored = preferences.snapshot.first()
            val presets = TuningPresets.forInstrument(stored.instrument, stored.guitarStrings)
            val preset = presets.firstOrNull { it.id == stored.presetId } ?: presets.first()
            _uiState.update {
                it.copy(
                    loaded = true,
                    instrument = stored.instrument,
                    guitarStrings = stored.guitarStrings,
                    preset = preset,
                    presets = presets,
                    mode = stored.mode,
                    loopTone = stored.loopTone,
                    microphone = permission.status.value,
                )
            }
            applyAudio()
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
        cancelPendingAudition()
        _uiState.update { it.copy(mode = mode, audition = null) }
        persist { it.copy(mode = mode) }
        applyAudio()
    }

    /** Persists the toggle and re-arms a sounding tone without restarting it (spec FR-019). */
    fun setLoopTone(on: Boolean) {
        if (_uiState.value.loopTone == on) return
        _uiState.update { state -> state.copy(loopTone = on, audition = state.audition?.copy(loop = on)) }
        persist { it.copy(loopTone = on) }
        applyAudio()
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

    private fun applyAudio() {
        val state = _uiState.value
        if (!state.loaded) return
        val inputs = PolicyInputs(state.mode, screenVisible, foreground, state.microphone, state.audition, state.interrupted, state.testSignal)
        val desired = ListeningPolicy.desired(inputs)
        requestPermissionIfNeeded(state)
        if (desired != current) {
            val previous = current
            if (previous is DesiredAudio.Tone && desired is DesiredAudio.Tone && previous.sound == desired.sound && desired.sound is ReferenceSound.Synth) {
                // Same tone, other loop setting: the engine re-arms without a restart (spec FR-019).
                current = if (startTone(desired)) desired else DesiredAudio.Idle
            } else {
                stopAudio(previous)
                current = startAudio(desired)
            }
            if (current == DesiredAudio.Idle && desired is DesiredAudio.Tone) _uiState.update { it.copy(audition = null) }
        }
        _uiState.update { it.copy(audio = audio.audioState) }
        updatePolling()
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

    private fun startAudio(desired: DesiredAudio): DesiredAudio = when (desired) {
        DesiredAudio.Idle -> DesiredAudio.Idle
        DesiredAudio.Listening -> if (audio.startListening() == AudioResult.Ok) desired else DesiredAudio.Idle
        is DesiredAudio.TestListening -> if (audio.startTestSignal(desired.signal) == AudioResult.Ok) desired else DesiredAudio.Idle
        is DesiredAudio.Tone -> if (startTone(desired)) desired else DesiredAudio.Idle
    }

    /** True when the engine sounds [tone]; a recording whose decode failed has no sample to play. */
    private fun startTone(tone: DesiredAudio.Tone): Boolean = when (val sound = tone.sound) {
        is ReferenceSound.Synth -> audio.startTone(sound.frequencyHz, if (tone.loop) 0 else ONE_SHOT_MILLIS) == AudioResult.Ok
        is ReferenceSound.Recording -> {
            val handle = sampleHandles[sound.resourceName]
            handle != null && audio.playSample(handle, tone.loop) == AudioResult.Ok
        }
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
            if (_uiState.value.reading != null) _uiState.update { it.copy(reading = null) }
        }
    }

    /** The 10 Hz mirror of the engine: the reading for semantics and the state; restarts what the engine dropped. */
    private fun poll() {
        audio.readPitch(snapshot)
        val engineState = audio.audioState
        val reading = if (engineState == AudioState.Listening && snapshot.hasPitch) MeterReading.from(snapshot) else null
        _uiState.update { it.copy(audio = engineState, reading = reading) }
        if (engineState == AudioState.Idle && current != DesiredAudio.Idle) {
            val wasTone = current is DesiredAudio.Tone
            current = DesiredAudio.Idle
            if (wasTone) _uiState.update { it.copy(audition = null) }
            applyAudio()
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
                stopAudio(current)
                current = DesiredAudio.Idle
                applyAudio()
            }
            AudioEvent.OutputRouteLost -> {
                cancelPendingAudition()
                _uiState.update { it.copy(audition = null) }
                applyAudio()
            }
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
        stopAudio(current)
        current = DesiredAudio.Idle
        unloadSamples()
        super.onCleared()
    }

    private companion object {
        const val ONE_SHOT_MILLIS = 2000
        const val POLL_MILLIS = 100L
    }
}
