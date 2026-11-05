package audio.tembra.ui.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.CustomSoundRef
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.SyncopationReading
import audio.tembra.core.metronome.TapTempoSequence
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.data.metronome.CustomClickSoundStore
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.metronome.StageResult
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.ClickLevel
import audio.tembra.engine.ClickSoundId
import audio.tembra.engine.CustomSoundResult
import audio.tembra.engine.EngineDispatchers
import audio.tembra.engine.MetronomeAudio
import audio.tembra.engine.MetronomeGrid
import audio.tembra.engine.TickSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.AudioPickFailure
import audio.tembra.ui.platform.AudioPickRequest
import audio.tembra.ui.platform.AudioPickResult
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.PlaybackNotification
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext

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
 * Tap tempo (User Story 3) owns one more job, the caption timer: each tap cancels the previous one and launches its
 * replacement, so the sequence ends two seconds after the last tap only; leaving the screen cancels it and ends the
 * sequence at once.
 *
 * [store], [picker] and [prepareDispatcher] serve the custom-sound import (User Story 5): each import is one
 * `viewModelScope` launch that suspends on the picker (feature 009's [AudioFilePicker] with the WAV filter, which
 * hands over a content handle), the store's staged copy and the engine's preparation (on [prepareDispatcher]); it
 * outlives the screen's visibility, so a file picked while the app was in the picker is still prepared.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class MetronomeViewModel(
    private val audio: MetronomeAudio,
    private val host: BackgroundPlaybackHost,
    private val preferences: MetronomePreferences,
    private val store: CustomClickSoundStore,
    private val picker: AudioFilePicker,
    /** Where a custom sound is decoded; an optional binding, so the graph provides nothing for it. */
    private val prepareDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Where the click starts, stops and previews (feature 010, T103): the session activates and the stream opens
     * there, about 100 ms on an iPhone 14, which on the main thread dropped frames on the Play tap. The engine's one
     * serial dispatcher, shared with the tuner and the transport, so the hand-over between screens keeps its order;
     * an optional binding.
     */
    private val audioDispatcher: CoroutineDispatcher = EngineDispatchers.blocking,
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
    private var tapCaptionJob: Job? = null

    /** What the Custom entry read when the running import began; a cancelled or refused import restores it. */
    private var customBeforeImport: CustomSoundState = CustomSoundState.None

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
            // A tap sequence ends with the screen; a returning user starts a new one.
            _uiState.update { it.copy(tap = TapTempoSequence(), tapCaption = TapCaption.Rest) }
        }
    }

    /**
     * Applies the stored settings once and programs the engine with them. The ViewModel is the only writer of the
     * preferences, so later emissions are its own writes and the state already holds them.
     *
     * A stored custom sound is prepared after that, off the main thread; until it is loaded the engine plays Classic
     * Wood in its place. The preparation is part of this job, so leaving the screen can cancel it halfway: the next
     * [screenVisible] finds the custom sound still unprepared and prepares it again.
     */
    private suspend fun observePreferences() {
        if (!_uiState.value.loaded) {
            val stored = preferences.snapshot.first().settings
            // From the stored settings to the first content and the grid handed to the engine: the metronome.load section (feature 007).
            PerfTrace.section(PerfSection.METRONOME_LOAD) {
                _uiState.update { it.copy(loaded = true, settings = stored) }
                sendGrid()
            }
            val sound = stored.sound
            selectEngineSound(if (sound is ClickSound.Custom) ClickSoundId.ClassicWood else soundIdOf(sound))
        }
        val state = _uiState.value
        val custom = state.settings.customSound
        if (custom != null && state.customSound == CustomSoundState.None) prepareStoredCustomSound(custom)
    }

    /**
     * Loads the imported sound named in the preferences. When the app's copy cannot be loaded the entry reads the name
     * greyed ([CustomSoundState.Unavailable]); if the custom sound was the selected one, Classic Wood (already in the
     * engine) takes its place, is persisted and the user is told once (spec FR-024). The next launch finds Classic Wood
     * selected, so the message is not repeated.
     *
     * An import started meanwhile owns the Custom entry: while it runs, the result is kept for it to restore on a
     * cancellation or refusal instead of overwriting [CustomSoundState.Importing]; once it has finished, the result is
     * dropped.
     */
    private suspend fun prepareStoredCustomSound(custom: CustomSoundRef) {
        val result = withContext(prepareDispatcher) { audio.loadCustomSound(custom.path) }
        val loaded = when (result) {
            is CustomSoundResult.Loaded -> true
            CustomSoundResult.NotWav, CustomSoundResult.Silent, CustomSoundResult.Missing -> false
        }
        val customState = if (loaded) CustomSoundState.Available(custom.displayName) else CustomSoundState.Unavailable(custom.displayName)
        when (_uiState.value.customSound) {
            CustomSoundState.None -> _uiState.update { it.copy(customSound = customState) }
            CustomSoundState.Importing -> customBeforeImport = customState
            // An import finished first: the store's file and the entry are its own now.
            is CustomSoundState.Available, is CustomSoundState.Unavailable -> return
        }
        if (_uiState.value.settings.sound !is ClickSound.Custom) return
        if (loaded) {
            selectEngineSound(ClickSoundId.Custom)
        } else {
            val fallback = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood)
            _uiState.update { it.copy(settings = it.settings.copy(sound = fallback)) }
            persist { it.copy(sound = fallback) }
            _messages.trySend(MetronomeMessage.CustomSoundMissing)
        }
    }

    private suspend fun observeAudioEvents() {
        audio.events.collect { event ->
            when (event) {
                AudioEvent.Interrupted -> onPlaybackEvent(PlaybackEvent.AudioInterrupted)
                AudioEvent.OutputRouteLost -> onPlaybackEvent(PlaybackEvent.OutputRouteLost)
                AudioEvent.DeviceLost -> onPlaybackEvent(PlaybackEvent.DeviceLost)
                // The metronome never restarts by itself (spec FR-027): the end of an interruption changes nothing.
                AudioEvent.InterruptionEnded -> Unit
                // Feature 010: the recording session's route changes concern the timeline only.
                AudioEvent.InputRouteChanged, is AudioEvent.OutputRouteChanged -> Unit
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
        // The metronome.tempoChange section in its animation variant (feature 007): iOS attributes the dial's frame
        // health to it; the interval is the intent's own work.
        PerfTrace.animation(PerfSection.METRONOME_TEMPO_CHANGE) {
            _uiState.update { it.copy(settings = it.settings.copy(tempo = tempo)) }
            sendGrid()
            persist { it.copy(tempo = tempo) }
        }
    }

    /** Moves the tempo by [delta], held inside 40–240; at an end nothing changes and nothing is written. */
    fun stepTempo(delta: Int) {
        setTempo(_uiState.value.settings.tempo.plus(delta))
    }

    // --- Tap tempo (spec FR-015 to FR-017) -----------------------------------------------------------------------------

    /**
     * A tap at [atNanos] (the frame time of the tap). From the second tap of a sequence the tempo follows the taps
     * through [setTempo], so it is persisted and reaches a running engine without a restart. The caption timer is
     * replaced, so the sequence ends [TapTempoSequence.WINDOW_NANOS] after the last tap.
     */
    fun tap(atNanos: Long) {
        val state = _uiState.value
        if (!state.loaded) return
        val sequence = state.tap.tap(atNanos)
        // The sequence keeps the last nine taps for the average; the caption counts every tap of the run.
        val counted = when (val previous = state.tapCaption) {
            TapCaption.Rest -> 0
            TapCaption.Again -> 1
            is TapCaption.Counting -> previous.taps
        }
        val caption = if (sequence.count < 2) TapCaption.Again else TapCaption.Counting(counted + 1)
        _uiState.update { it.copy(tap = sequence, tapCaption = caption) }
        sequence.tempo?.let(::setTempo)
        tapCaptionJob?.cancel()
        tapCaptionJob = viewModelScope.launch { endTapSequenceAfterTheWindow() }
    }

    private suspend fun endTapSequenceAfterTheWindow() {
        delay(TAP_WINDOW_MILLIS)
        _uiState.update { it.copy(tap = TapTempoSequence(), tapCaption = TapCaption.Rest) }
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

    // --- Click sounds (spec FR-020 to FR-022) --------------------------------------------------------------------------

    /**
     * Selects [sound]: the engine swaps it from its next tick, and while stopped one Click-level preview sounds. A
     * preview the engine refuses (a previous one still sounding) is ignored. The Custom entry selects the imported sound
     * when it is available and opens the picker otherwise; the selected sound selects nothing.
     */
    fun selectSound(sound: ClickSound) {
        val state = _uiState.value
        if (!state.loaded || state.settings.sound == sound) return
        if (sound is ClickSound.Custom) {
            when (state.customSound) {
                is CustomSoundState.Available -> Unit
                CustomSoundState.None, is CustomSoundState.Unavailable -> {
                    importCustomSound()
                    return
                }
                CustomSoundState.Importing -> return
            }
        }
        _uiState.update { it.copy(settings = it.settings.copy(sound = sound)) }
        selectEngineSound(soundIdOf(sound))
        if (state.playback == PlaybackState.Stopped) previewClick()
        persist { it.copy(sound = sound) }
    }

    /**
     * Imports a WAV for the Custom entry (spec FR-022 to FR-025, contracts/click-sounds.md): the platform picker copies
     * the file to the store's staging path, the engine validates and prepares the staged file off the main thread, and
     * only then does the store commit it as the app's copy. A loaded sound is selected, previewed while stopped and
     * persisted with its name and path; a refusal removes the staged file only, keeps the previous sound and copy
     * (reloading a selected custom sound into the engine) and sends its message once. The entry reads
     * [CustomSoundState.Importing] throughout, and a second import meanwhile is ignored.
     */
    fun importCustomSound() {
        val state = _uiState.value
        if (!state.loaded || state.customSound == CustomSoundState.Importing) return
        customBeforeImport = state.customSound
        _uiState.update { it.copy(customSound = CustomSoundState.Importing) }
        viewModelScope.launch { runImport() }
    }

    /** The filled Custom entry's Replace action: the same flow, the new file replacing the previous one (spec FR-022). */
    fun replaceCustomSound() = importCustomSound()

    /** How one import ended. */
    private sealed interface ImportOutcome {
        data class Imported(val name: String) : ImportOutcome
        data object Cancelled : ImportOutcome
        data class Refused(val message: MetronomeMessage) : ImportOutcome
    }

    /** One import, start to end; whatever did not end in a loaded sound restores the entry it found. */
    private suspend fun runImport() {
        var imported = false
        try {
            val outcome = try {
                pickAndPrepareCustomSound()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The store's file system, the picker or the engine failed in a way none of them could report.
                ImportOutcome.Refused(MetronomeMessage.ImportFailed)
            }
            when (outcome) {
                is ImportOutcome.Imported -> {
                    imported = true
                    useImportedSound(outcome.name)
                }
                ImportOutcome.Cancelled -> Unit
                is ImportOutcome.Refused -> refuse(outcome.message)
            }
        } finally {
            if (!imported) _uiState.update { it.copy(customSound = customBeforeImport) }
        }
    }

    /**
     * Picks a WAV, stages its bytes (the store copies them with the 64 MB cap) and has the engine prepare the staged
     * file; the store commits it only once the engine has accepted it, so a refused file never replaces the previous
     * copy (spec FR-025).
     */
    private suspend fun pickAndPrepareCustomSound(): ImportOutcome {
        val file = when (val picked = picker.pickOne(AudioPickRequest.wav)) {
            is AudioPickResult.Picked -> picked.files.firstOrNull() ?: return ImportOutcome.Cancelled
            AudioPickResult.Cancelled -> return ImportOutcome.Cancelled
            is AudioPickResult.Failed -> return ImportOutcome.Refused(messageOf(picked.reason))
        }
        val name = file.displayName.substringBeforeLast('.').ifBlank { FALLBACK_NAME }
        when (store.stage(file::open)) {
            StageResult.Staged -> Unit
            StageResult.TooLarge -> return ImportOutcome.Refused(MetronomeMessage.TooLarge)
            StageResult.Unreadable -> return ImportOutcome.Refused(MetronomeMessage.ImportFailed)
        }
        return when (withContext(prepareDispatcher) { audio.loadCustomSound(store.stagingPath) }) {
            // The engine accepted the staged file; a commit that finds nothing staged is a failed import.
            is CustomSoundResult.Loaded ->
                if (store.commit()) ImportOutcome.Imported(name) else ImportOutcome.Refused(MetronomeMessage.ImportFailed)
            CustomSoundResult.NotWav -> ImportOutcome.Refused(MetronomeMessage.NotWav)
            CustomSoundResult.Silent -> ImportOutcome.Refused(MetronomeMessage.Silent)
            CustomSoundResult.Missing -> ImportOutcome.Refused(MetronomeMessage.ImportFailed)
        }
    }

    /**
     * Discards a refused import: the staged file (or a partial copy a picker left) goes, the previous copy stays. The
     * engine may have prepared the staged file in place of the previous sound, so a selected custom sound is loaded
     * again from its copy. The message is sent once, whether or not the staged file could be removed.
     */
    private suspend fun refuse(message: MetronomeMessage) {
        try {
            store.deleteStaging()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A staged file that could not be removed is overwritten by the next pick and never committed.
        }
        if (_uiState.value.settings.sound is ClickSound.Custom) reloadPreviousCustomSound()
        _messages.trySend(message)
    }

    private suspend fun reloadPreviousCustomSound() {
        when (withContext(prepareDispatcher) { audio.loadCustomSound(store.path) }) {
            is CustomSoundResult.Loaded -> selectEngineSound(ClickSoundId.Custom)
            // The previous copy is gone too; the next launch's fallback (spec FR-025) handles it.
            CustomSoundResult.NotWav, CustomSoundResult.Silent, CustomSoundResult.Missing -> Unit
        }
    }

    private fun messageOf(failure: AudioPickFailure): MetronomeMessage = when (failure) {
        AudioPickFailure.Unreadable -> MetronomeMessage.ImportFailed
    }

    /** Selects the sound the engine just loaded: from its next tick while running, with one preview while stopped. */
    private fun useImportedSound(name: String) {
        val sound = ClickSound.Custom(name)
        val ref = CustomSoundRef(name, store.path)
        _uiState.update {
            it.copy(customSound = CustomSoundState.Available(name), settings = it.settings.copy(sound = sound, customSound = ref))
        }
        selectEngineSound(ClickSoundId.Custom)
        if (_uiState.value.playback == PlaybackState.Stopped) previewClick()
        persist { it.copy(sound = sound, customSound = ref) }
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

    /**
     * `keepAlive` → `startMetronome` (data-model.md §7); a refusal on either side ends in a stop with its message. The
     * screen shows Starting at once; the engine start runs on [audioDispatcher] (T103) and, once back, becomes Running
     * only if nothing stopped the click meanwhile (a pause that landed during the start has its own stop queued behind
     * the start on the same dispatcher). Non-cancellable, so a cleared screen still settles the engine.
     */
    // ATOMIC start (a delicate API since kotlinx.coroutines 1.11): the engine call runs even when the screen is cleared at once.
    @OptIn(DelicateCoroutinesApi::class)
    private fun startPlayback() {
        _uiState.update { it.copy(playback = PlaybackState.Starting) }
        val notification = PlaybackNotification(MetronomeCopy.notificationTitle, MetronomeCopy.notificationSubtitle(_uiState.value.settings.tempo))
        if (!host.keepAlive(notification)) {
            onPlaybackEvent(PlaybackEvent.StartFailed(StartFailure.HostRefused))
            return
        }
        viewModelScope.launch(CoroutineName("startMetronome"), start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                val failure = withContext(audioDispatcher) {
                    // A preview click still sounding would make the engine refuse the start.
                    if (audio.audioState == AudioState.Tone) audio.stopTone()
                    startFailureOf(audio.startMetronome())
                }
                if (_uiState.value.playback != PlaybackState.Starting) return@withContext
                if (failure != null) {
                    onPlaybackEvent(PlaybackEvent.StartFailed(failure))
                    return@withContext
                }
                pollsSinceStart = 0
                _uiState.update { it.copy(playback = PlaybackState.Running) }
            }
        }
    }

    /** The preview click on [audioDispatcher] (T103); the engine's own refusal (a tone still sounding) is the result. */
    private fun previewClick() {
        viewModelScope.launch(CoroutineName("previewClick")) { withContext(audioDispatcher) { audio.previewClick(ClickLevel.Click) } }
    }

    /** Android requests audio focus inside the engine start, so a denial arrives as [AudioResult.FOCUS_DENIED]. */
    private fun startFailureOf(result: AudioResult): StartFailure? = when (result) {
        AudioResult.Ok -> null
        is AudioResult.Error if result.code == AudioResult.FOCUS_DENIED -> StartFailure.FocusDenied
        is AudioResult.Error -> StartFailure.EngineError
    }

    /**
     * `stopMetronome` → `release` (data-model.md §7). Idempotent on both sides, so it also undoes a start that failed
     * halfway: the engine was not started, and the host gives back whatever it had acquired.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun stopPlayback(message: MetronomeMessage?) {
        _uiState.update { it.copy(playback = PlaybackState.Stopped, currentBeat = NO_BEAT) }
        if (message != null) _messages.trySend(message)
        // The engine stop on the audio dispatcher (T103), then the host's release; atomic and non-cancellable, so the
        // stop a cleared screen asks for still reaches the engine after its scope is gone.
        viewModelScope.launch(CoroutineName("stopMetronome"), start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                withContext(audioDispatcher) { audio.stopMetronome() }
                host.release()
            }
        }
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
        val grid = MetronomeGrid.from(state.grid, state.settings.tempo)
        engineCommand("setGrid") { audio.setGrid(grid) }
    }

    private fun selectEngineSound(sound: ClickSoundId) = engineCommand("selectSound") { audio.selectSound(sound) }

    /**
     * The grid and sound commands run on [audioDispatcher] like the start and the stop: they take the engine's command
     * lock, which a transport close queued there holds while it stops the timeline's stream, so from the main thread they
     * waited for it (up to 160 ms when the Metronome opened straight from the Timeline, the 010 post-merge sessions). On
     * the one serial dispatcher they keep their order with each other and with a start, stop or preview launched after them.
     */
    private fun engineCommand(name: String, command: () -> Unit) {
        viewModelScope.launch(CoroutineName(name)) { withContext(audioDispatcher) { command() } }
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
        tapCaptionJob?.cancel()
        tapCaptionJob = null
    }

    override fun onCleared() {
        onPlaybackEvent(PlaybackEvent.ScreenHidden)
        cancelJobs()
        super.onCleared()
    }

    private companion object {
        const val NO_BEAT = -1
        const val POLL_MILLIS = 100L
        const val TAP_WINDOW_MILLIS = TapTempoSequence.WINDOW_NANOS / 1_000_000L

        /** The polls after a start at which the output latency is measured again: about 100 ms, 300 ms and 1 s. */
        val LATENCY_REFRESH_POLLS = setOf(1, 3, 10)

        const val LIGHT_MAX_NANOS = 150_000_000L
        const val LIGHT_BEAT_SHARE = 0.6

        /** The label of a picked sound whose file name is empty. */
        const val FALLBACK_NAME = "Custom sound"
    }
}
