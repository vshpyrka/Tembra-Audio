package audio.tembra.ui.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.SyncopationReading
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.data.metronome.CustomClickSoundStore
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.ClickSoundId
import audio.tembra.engine.MetronomeAudio
import audio.tembra.engine.MetronomeGrid
import audio.tembra.engine.TickSnapshot
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.SoundFilePicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The Metronome destination's ViewModel (contracts/metronome-screen.md, research R12). The click runs in the engine;
 * this class programs it (grid and sound), owns the playback lifecycle through [PlaybackPolicy] and persists the
 * settings through [MetronomePreferences]. The beat light is read in the draw phase through [telemetry]; a 10 Hz poll
 * mirrors the playing beat for semantics and notices a lost stream.
 *
 * Owners (`kotlin-concurrency-and-flow`): the constructor launches nothing. [screenVisible] `true` starts the named
 * jobs [observePreferences], [observeAudioEvents], [observeStopRequests] and [pollAudioState] on [viewModelScope];
 * `false` stops the click and cancels them; [onCleared] stops everything. Each persisting intent is one named
 * `viewModelScope` launch over the suspending [MetronomePreferences.update]. The engine and host calls are
 * non-blocking and made on the main thread, in the contract's order: `keepAlive` before `startMetronome`,
 * `stopMetronome` before `release`.
 *
 * [store], [picker] and [prepareDispatcher] serve the custom-sound import (User Story 5).
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class MetronomeViewModel(
    private val audio: MetronomeAudio,
    private val host: BackgroundPlaybackHost,
    private val preferences: MetronomePreferences,
    private val store: CustomClickSoundStore,
    private val picker: SoundFilePicker,
    /** Where a custom sound is decoded; an optional binding, so the graph provides nothing for it. */
    private val prepareDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetronomeUiState())
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    /** One-shot snackbar messages: exactly once, to the one collector (the wiring's snackbar effect). */
    private val _messages = Channel<MetronomeMessage>(Channel.BUFFERED)
    val messages: Flow<MetronomeMessage> = _messages.receiveAsFlow()

    /** Draw-phase handle: at most one engine read per frame, memoised on the frame time (research R7). */
    val telemetry: BeatTelemetry = BeatTelemetry(::litBeatAt)

    private val frameTick = TickSnapshot()
    private var lastFrameNanos = Long.MIN_VALUE
    private var lastLitBeat = NO_BEAT

    private val pollTick = TickSnapshot()
    private var pollsSinceStart = 0

    private var preferencesJob: Job? = null
    private var audioEventsJob: Job? = null
    private var stopRequestsJob: Job? = null
    private var pollJob: Job? = null

    // --- Visibility --------------------------------------------------------------------------------------------------

    /** The Metronome entry is shown or left. Leaving stops the click (spec FR-027); nothing restarts it on return. */
    fun screenVisible(visible: Boolean) {
        if (visible) {
            if (preferencesJob == null) preferencesJob = viewModelScope.launch { observePreferences() }
            if (audioEventsJob == null) audioEventsJob = viewModelScope.launch { observeAudioEvents() }
            if (stopRequestsJob == null) stopRequestsJob = viewModelScope.launch { observeStopRequests() }
            if (pollJob == null) pollJob = viewModelScope.launch { pollAudioState() }
        } else {
            onPlaybackEvent(PlaybackEvent.ScreenHidden)
            cancelJobs()
        }
    }

    /**
     * Applies the stored settings once and programs the engine with them. The ViewModel is the only writer of the
     * preferences, so later emissions are its own writes and the state already holds them.
     */
    private suspend fun observePreferences() {
        if (_uiState.value.loaded) return
        val stored = preferences.snapshot.first().settings
        _uiState.update { it.copy(loaded = true, settings = stored) }
        sendGrid()
        audio.selectSound(soundIdOf(stored.sound))
    }

    private suspend fun observeAudioEvents() {
        audio.events.collect { event ->
            when (event) {
                AudioEvent.Interrupted -> onPlaybackEvent(PlaybackEvent.AudioInterrupted)
                AudioEvent.OutputRouteLost -> onPlaybackEvent(PlaybackEvent.OutputRouteLost)
                AudioEvent.DeviceLost -> onPlaybackEvent(PlaybackEvent.DeviceLost)
                // The metronome never restarts by itself (spec FR-027): the end of an interruption changes nothing.
                AudioEvent.InterruptionEnded -> Unit
            }
        }
    }

    private suspend fun observeStopRequests() {
        host.stopRequests.collect { onPlaybackEvent(PlaybackEvent.StopRequested) }
    }

    /** The 10 Hz mirror of the engine while the screen is visible. */
    private suspend fun pollAudioState() {
        while (true) {
            poll()
            delay(POLL_MILLIS)
        }
    }

    private fun poll() {
        val state = _uiState.value
        if (state.playback != PlaybackState.Running) {
            if (state.currentBeat != NO_BEAT) _uiState.update { it.copy(currentBeat = NO_BEAT) }
            return
        }
        audio.readTick(pollTick)
        // The engine can stay in the Metronome state after a failed reopen, so the tick's own flag counts too.
        if (audio.audioState != AudioState.Metronome || !pollTick.running) {
            onPlaybackEvent(PlaybackEvent.DeviceLost)
            return
        }
        pollsSinceStart++
        // Android measures the output latency right after the stream opens, where it usually fails: ask again.
        if (pollsSinceStart in LATENCY_REFRESH_POLLS) audio.reportOutputLatencyNanos(0)
        if (state.currentBeat != pollTick.beatIndex) _uiState.update { it.copy(currentBeat = pollTick.beatIndex) }
    }

    // --- Tempo (spec FR-002 to FR-005) --------------------------------------------------------------------------------

    fun setTempo(tempo: Tempo) {
        val state = _uiState.value
        if (!state.loaded || state.settings.tempo == tempo) return
        _uiState.update { it.copy(settings = it.settings.copy(tempo = tempo)) }
        sendGrid()
        persist { it.copy(tempo = tempo) }
    }

    /** Moves the tempo by [delta], held inside 40–240; at an end nothing changes and nothing is written. */
    fun stepTempo(delta: Int) {
        setTempo(_uiState.value.settings.tempo.plus(delta))
    }

    // --- Bar shape and styles (spec FR-006 to FR-010) ------------------------------------------------------------------

    /**
     * Changes the bar's length. The selected style is re-applied at the new length; with none (cells edited by hand)
     * the remaining beats keep their levels and new beats are Click, and the style is whatever the result matches.
     */
    fun selectSignature(signature: TimeSignature) {
        updateSettings { settings ->
            val style = settings.style
            if (style != null) {
                settings.copy(signature = signature, pattern = style.pattern(signature))
            } else {
                val pattern = settings.pattern.resizedTo(signature.beats)
                settings.copy(signature = signature, pattern = pattern, style = MetronomeStyle.matching(pattern, signature))
            }
        }
    }

    fun selectSubdivision(subdivision: Subdivision) {
        updateSettings { it.copy(subdivision = subdivision) }
    }

    /** Advances the level of the 0-based [index]; the style follows the cells (none when they match no style). */
    fun cycleBeat(index: Int) {
        if (index !in _uiState.value.settings.pattern.levels.indices) return
        updateSettings { settings ->
            val pattern = settings.pattern.cycle(index)
            settings.copy(pattern = pattern, style = MetronomeStyle.matching(pattern, settings.signature))
        }
    }

    /** Rewrites every cell from [style] (spec FR-010). */
    fun selectStyle(style: MetronomeStyle) {
        updateSettings { it.copy(pattern = style.pattern(it.signature), style = style) }
    }

    /**
     * The debug build's Syncopated reading (research R14); null restores the shipped one. It is not persisted. Called
     * before [screenVisible], it only sets the reading, and the first grid sent on loading carries it.
     */
    fun useDebugSettings(request: MetronomeDebugRequest?) {
        val reading = request?.syncopation ?: SyncopationReading.EvenBeats
        if (_uiState.value.syncopationReading == reading) return
        _uiState.update { it.copy(syncopationReading = reading) }
        if (_uiState.value.loaded) sendGrid()
    }

    /**
     * Applies a settings change once loaded: the new state, the engine's grid (applied from its next block while
     * running) and the stored copy. A change that leaves the settings as they are does nothing.
     */
    private fun updateSettings(transform: (MetronomeSettings) -> MetronomeSettings) {
        val state = _uiState.value
        if (!state.loaded) return
        val next = transform(state.settings)
        if (next == state.settings) return
        _uiState.update { it.copy(settings = next) }
        sendGrid()
        persist { it.copy(signature = next.signature, subdivision = next.subdivision, pattern = next.pattern, style = next.style) }
    }

    // --- Playback (spec FR-001, FR-027) -------------------------------------------------------------------------------

    fun togglePlayback() {
        val state = _uiState.value
        if (!state.loaded) return
        val event = if (state.playback == PlaybackState.Stopped) PlaybackEvent.PlayPressed else PlaybackEvent.PausePressed
        onPlaybackEvent(event)
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
        when (val transition = PlaybackPolicy.onEvent(_uiState.value.playback, event)) {
            PlaybackTransition.Start -> startPlayback()
            is PlaybackTransition.Stop -> stopPlayback(transition.message)
            PlaybackTransition.Ignore -> Unit
        }
    }

    /** `keepAlive` → `startMetronome` (data-model.md §7); a refusal on either side ends in a stop with its message. */
    private fun startPlayback() {
        // A preview click still sounding would make the engine refuse the start.
        if (audio.audioState == AudioState.Tone) audio.stopTone()
        _uiState.update { it.copy(playback = PlaybackState.Starting) }
        val failure = when {
            !host.keepAlive() -> StartFailure.HostRefused
            audio.startMetronome() is AudioResult.Error -> StartFailure.EngineError
            else -> null
        }
        if (failure != null) {
            onPlaybackEvent(PlaybackEvent.StartFailed(failure))
            return
        }
        pollsSinceStart = 0
        _uiState.update { it.copy(playback = PlaybackState.Running) }
    }

    /**
     * `stopMetronome` → `release` (data-model.md §7). Idempotent on both sides, so it also undoes a start that failed
     * halfway: the engine was not started, and the host gives back whatever it had acquired.
     */
    private fun stopPlayback(message: MetronomeMessage?) {
        audio.stopMetronome()
        host.release()
        _uiState.update { it.copy(playback = PlaybackState.Stopped, currentBeat = NO_BEAT) }
        if (message != null) _messages.trySend(message)
    }

    // --- Telemetry ----------------------------------------------------------------------------------------------------

    /**
     * The beat to light at [frameNanos]: the last tick's beat once it is audible and for `min(150 ms, 0.6 × beat)`
     * after (research R7). Every cell asks in the same frame, so the engine is read once per distinct frame time.
     */
    private fun litBeatAt(frameNanos: Long): Int {
        if (frameNanos != lastFrameNanos) {
            lastFrameNanos = frameNanos
            audio.readTick(frameTick)
            val sinceAudible = -frameTick.audibleInNanos
            val window = min(LIGHT_MAX_NANOS, (frameTick.beatPeriodNanos * LIGHT_BEAT_SHARE).toLong())
            lastLitBeat = if (frameTick.running && sinceAudible >= 0 && sinceAudible < window) frameTick.beatIndex else NO_BEAT
        }
        return lastLitBeat
    }

    // --- Engine and preferences ------------------------------------------------------------------------------------------

    /** The engine accepts a grid in every state and applies it from its next block (research R1), so it always gets the current one. */
    private fun sendGrid() {
        val state = _uiState.value
        audio.setGrid(MetronomeGrid.from(state.grid, state.settings.tempo))
    }

    private fun persist(transform: (MetronomeSettings) -> MetronomeSettings) {
        viewModelScope.launch { writeSettings(transform) }
    }

    private suspend fun writeSettings(transform: (MetronomeSettings) -> MetronomeSettings) {
        preferences.update { snapshot -> snapshot.copy(settings = transform(snapshot.settings)) }
    }

    private fun soundIdOf(sound: ClickSound): ClickSoundId = when (sound) {
        is ClickSound.BuiltIn -> when (sound.sound) {
            BuiltInClickSound.ClassicWood -> ClickSoundId.ClassicWood
            BuiltInClickSound.Rim808 -> ClickSoundId.Rim808
            BuiltInClickSound.DigitalBeep -> ClickSoundId.DigitalBeep
            BuiltInClickSound.Cowbell -> ClickSoundId.Cowbell
            BuiltInClickSound.Mechanical -> ClickSoundId.Mechanical
        }
        is ClickSound.Custom -> ClickSoundId.Custom
    }

    private fun cancelJobs() {
        preferencesJob?.cancel()
        preferencesJob = null
        audioEventsJob?.cancel()
        audioEventsJob = null
        stopRequestsJob?.cancel()
        stopRequestsJob = null
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        onPlaybackEvent(PlaybackEvent.ScreenHidden)
        cancelJobs()
        super.onCleared()
    }

    private companion object {
        const val NO_BEAT = -1
        const val POLL_MILLIS = 100L

        /** The polls after a start at which the output latency is measured again: about 100 ms, 300 ms and 1 s. */
        val LATENCY_REFRESH_POLLS = setOf(1, 3, 10)

        const val LIGHT_MAX_NANOS = 150_000_000L
        const val LIGHT_BEAT_SHARE = 0.6
    }
}
