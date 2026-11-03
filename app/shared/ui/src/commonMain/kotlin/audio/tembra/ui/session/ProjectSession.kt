package audio.tembra.ui.session

import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.project.AddTrack
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipTemplate
import audio.tembra.core.project.DeleteClip
import audio.tembra.core.project.DeleteTrack
import audio.tembra.core.project.EditHistory
import audio.tembra.core.project.EditRejection
import audio.tembra.core.project.EditResult
import audio.tembra.core.project.IdGenerator
import audio.tembra.core.project.ImportAsNewTracks
import audio.tembra.core.project.ImportClips
import audio.tembra.core.project.ImportToTrack
import audio.tembra.core.project.MoveClip
import audio.tembra.core.project.MoveTrack
import audio.tembra.core.project.NewTrackWithClip
import audio.tembra.core.project.PlaybackEvent
import audio.tembra.core.project.PlaybackPolicy
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.PlaybackTransition
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectEdit
import audio.tembra.core.project.ProjectEditor
import audio.tembra.core.project.ProjectId
import audio.tembra.core.project.RenameProject
import audio.tembra.core.project.RenameTrack
import audio.tembra.core.project.SetDescription
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetKey
import audio.tembra.core.project.SetLabel
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetMuted
import audio.tembra.core.project.SetSoloed
import audio.tembra.core.project.SetTempo
import audio.tembra.core.project.SetTimeSignature
import audio.tembra.core.project.SplitClip
import audio.tembra.core.project.TrackId
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.projects.AutosaveScheduler
import audio.tembra.data.projects.ImportRefusal
import audio.tembra.data.projects.ImportResult
import audio.tembra.data.projects.OpenResult
import audio.tembra.data.projects.OpenedProject
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.PickedFile
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.data.projects.ProjectViewState
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.MetronomeAudio
import audio.tembra.engine.PlayResult
import audio.tembra.engine.TimelineAudio
import audio.tembra.engine.TransportOpenResult
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.PlaybackNotification
import audio.tembra.ui.tracksettings.TrackSettingsDraft
import audio.tembra.ui.transport.TransportCopy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The open project's lifecycle (contracts/project-session.md; spec FR-005 to FR-008a, FR-035, FR-046): open, edit,
 * undo and redo, save, play, pause, seek, metronome and import, bound to the engine through the [TimelineAudio] seam
 * and to the document through the [ProjectRepository]. Lives with the app ([scope] is the graph's `session` scope and
 * is never cancelled); [start] is the one place its observers launch, called by [AppStartup].
 *
 * `kotlin-concurrency-and-flow`: [state] is the renderable truth with a synchronous value; [messages] is a buffered
 * channel to the one screen that shows them (a message must not be lost while the Timeline is not composed and must
 * show once); [dialog] is a `StateFlow` because the question stays visible until answered. Launch sites, each named:
 * `observeEngineEvents`, `observeMetronomePreferences`, `observeHostStopRequests`, `observeSeekRequests`, `autosave`
 * (the scheduler's own), `overviewPolling` (the tracker's), `startPlayback`, `importJob`, `saveViewState`, `flushSave`.
 */
@Inject
@SingleIn(AppScope::class)
class ProjectSession(
    private val repository: ProjectRepository,
    private val audio: TimelineAudio,
    metronome: MetronomeAudio,
    private val preferences: MetronomePreferences,
    private val host: BackgroundPlaybackHost,
    @Named("session") private val scope: CoroutineScope,
    /** Optional bindings: the graph provides neither, tests pass a scripted reader and the test dispatcher. */
    overviewReader: OverviewReader = SystemOverviewReader,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaybackGuard, SessionActivity {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Imports in flight (feature 011, FR-015a): counted, so overlapping imports keep the pointer busy until the last ends. */
    private val importsInFlight = MutableStateFlow(0)
    private val _busy = MutableStateFlow(false)
    override val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<SessionMessage>(Channel.BUFFERED)
    val messages: Flow<SessionMessage> = _messages.receiveAsFlow()

    private val _dialog = MutableStateFlow<DialogRequest?>(null)
    override val dialog: StateFlow<DialogRequest?> = _dialog.asStateFlow()
    override val isPlaying: Boolean get() = _state.value.isPlaying

    /** The unsaved track-settings form handed between the nested screen and the inline row (spec FR-042). */
    val settingsDraft: MutableStateFlow<TrackSettingsDraft?> = MutableStateFlow(null)

    /** Draw-phase handle: at most one engine read per frame, memoised on the frame time (research R7). */
    val transportTelemetry: TransportTelemetry = TransportTelemetry(::readFrame)

    private val sources = ClipSourceRegistry(audio)
    private val bridge = MetronomeBridge(metronome, audio)
    private val overviews = OverviewTracker(repository, overviewReader, ioDispatcher, scope, ::onOverview)
    private val autosave = AutosaveScheduler(scope, save = ::persist)
    private val seekRequests = Channel<Beats>(Channel.CONFLATED)
    private val importMutex = Mutex()

    private var started = false
    private var history: EditHistory? = null
    private var opened: OpenedProject? = null
    private var transportOpen = false
    /** The shell shows a destination that hosts the transport (the Timeline is the start destination). */
    private var hostVisible = true
    /** The device was handed over to another destination; [attachTransport] takes it back. */
    private var detached = false
    private var attachJob: Job? = null
    /** Opens and closes of the transport, one at a time: a second open while one is in flight would be refused by the engine and drop the audio focus with it. */
    private val transportMutex = Mutex()
    private var startJob: Job? = null
    private var settings = MetronomeSettings()
    private var lastEdit: ProjectEdit? = null
    private var referencedAssets: Set<AudioAssetId> = emptySet()
    private val pendingRetired = LinkedHashSet<AudioAssetId>()

    private val frameSnapshot = TransportSnapshot()
    private var lastFrameNanos = Long.MIN_VALUE
    private val positionSnapshot = TransportSnapshot()

    // --- Lifecycle ---------------------------------------------------------------------------------------------------

    /** Starts the observers once; later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        scope.launch(CoroutineName("observeEngineEvents")) { observeEngineEvents() }
        scope.launch(CoroutineName("observeMetronomePreferences")) { observeMetronomePreferences() }
        scope.launch(CoroutineName("observeHostStopRequests")) { observeHostStopRequests() }
        scope.launch(CoroutineName("observeSeekRequests")) { observeSeekRequests() }
        autosave.start()
    }

    /**
     * Opens [id]: pauses playback, saves and closes the current project, then binds the new one to the engine in the
     * contract's order (transport, clip sources, tempo, master, arrangement, click grid) before publishing it and
     * recording it as the open project. A document that cannot be opened leaves no project and reports the reason.
     */
    suspend fun open(id: ProjectId) {
        PerfTrace.section(PerfSection.TIMELINE_PROJECT_OPEN) {
            if (isPlaying) dispatch(PlaybackEvent.Pause)
            autosave.flush()
            closeCurrent()
            when (val result = repository.open(id)) {
                is OpenResult.Missing -> publishUnavailable(NOT_FOUND)
                is OpenResult.Unavailable -> publishUnavailable(result.reason)
                is OpenResult.Opened -> install(id, result.opened)
            }
        }
    }

    /** Saves the pending edits now; [AppLifecycleObserver] calls it on `ON_STOP` (spec FR-046). */
    fun flushSave() {
        scope.launch(CoroutineName("flushSave")) { autosave.flush() }
    }

    suspend fun flush() = autosave.flush()

    // --- Edits -------------------------------------------------------------------------------------------------------

    /**
     * Applies [edit] to the open project: the history grows (a gain, volume or tempo change following one of the same
     * kind replaces the last snapshot instead, so a drag is one undo step), the engine receives the smallest matching
     * update, and a save is scheduled. Returns the editor's reason when it refused; null when applied or no project.
     */
    fun edit(edit: ProjectEdit): EditRejection? {
        val current = history ?: return null
        return when (val result = ProjectEditor.apply(current.present, edit)) {
            is EditResult.Rejected -> result.reason
            is EditResult.Applied -> {
                val next = if (coalesces(edit, lastEdit)) current.copy(present = result.project) else current.push(result.project)
                lastEdit = edit
                history = next
                applyToEngine(result.project, edit)
                settle(next)
                null
            }
        }
    }

    /** Ends a coalesced run of gain or volume changes: the next one starts a new undo step. */
    fun closeEditGroup() {
        lastEdit = null
    }

    fun undo() {
        val current = history ?: return
        if (!current.canUndo) return
        restore(current.undo(), current.present)
    }

    fun redo() {
        val current = history ?: return
        if (!current.canRedo) return
        restore(current.redo(), current.present)
    }

    /** The metronome toggle is stored with the project but is not an undo step (spec FR-036). */
    fun setMetronome(on: Boolean) {
        val current = history ?: return
        if (current.present.metronomeOn == on) return
        val project = current.present.copy(metronomeOn = on)
        history = current.copy(present = project)
        bridge.setEnabled(on)
        autosave.onEdit()
        _state.update { it.copy(project = project, metronomeOn = on) }
    }

    /** Remembers the zoom and grid of the open project (`view.json`, never the document). */
    fun saveViewState(viewState: ProjectViewState) {
        val id = history?.present?.id ?: return
        _state.update { it.copy(viewState = viewState) }
        scope.launch(CoroutineName("saveViewState")) { repository.saveViewState(id, viewState) }
    }

    /** Copies [files] into the project and places them per [target]; refusals arrive as one message (spec FR-043, FR-044). */
    fun importAudio(files: List<PickedFile>, target: ImportTarget) {
        val projectId = history?.present?.id ?: return
        scope.launch(CoroutineName("importJob")) {
            // Counted inside the job: a job cancelled before it starts never counts, so the busy count never sticks.
            importStarted()
            try {
                importNow(projectId, files, target)
            } finally {
                importEnded()
            }
        }
    }

    private fun importStarted() {
        _busy.value = importsInFlight.updateAndGet { it + 1 } > 0
    }

    private fun importEnded() {
        _busy.value = importsInFlight.updateAndGet { it - 1 } > 0
    }

    // --- Playback ----------------------------------------------------------------------------------------------------

    fun play() = dispatch(PlaybackEvent.Play)

    fun pause() = dispatch(PlaybackEvent.Pause)

    fun seek(beats: Beats) = dispatch(PlaybackEvent.Seek(beats))

    fun skipToStart() = dispatch(PlaybackEvent.SkipToStart)

    fun stepBar(delta: Int) {
        val project = history?.present ?: return
        dispatch(PlaybackEvent.StepBar(delta, currentPosition(), project.timeSignature.beats))
    }

    /** The transport position now: the stopped position, or the engine's while it runs. */
    fun currentPosition(): Beats = when (val playback = _state.value.playback) {
        is PlaybackState.Stopped -> playback.positionBeats
        is PlaybackState.Starting -> playback.positionBeats
        PlaybackState.Playing, PlaybackState.Pausing -> readEnginePosition()
    }

    /** The engine's transport state for the 10 Hz mirror (semantics and tests); the per-frame readers use [transportTelemetry]. */
    fun readTransport(into: TransportSnapshot) = audio.readTransport(into)

    override suspend fun confirmLeave(): Boolean {
        if (!isPlaying) return true
        val answer = CompletableDeferred<Boolean>()
        _dialog.value = DialogRequest.StopAndLeave { result ->
            _dialog.value = null
            answer.complete(result)
        }
        if (!answer.await()) return false
        dispatch(PlaybackEvent.LeaveConfirmed)
        _state.first { it.playback is PlaybackState.Stopped }
        return true
    }

    override fun transportHostVisible(visible: Boolean) {
        hostVisible = visible
        if (visible) {
            launchAttach()
        } else {
            // A running attach is left alone: its loop ends on [hostVisible], and an open in flight closes itself.
            detachTransport()
        }
    }

    private fun launchAttach() {
        if (opened == null || !detached || attachJob?.isActive == true) return
        attachJob = scope.launch(CoroutineName("attachTransport")) { attachTransport() }
    }

    /**
     * Hands the audio device over (the tuner or the standalone metronome opens its stream next); the project stays
     * open. Also what an open that finishes after the host has gone does to itself.
     */
    private fun detachTransport() {
        if (transportOpen) {
            audio.closeTransport()
            transportOpen = false
        }
        if (opened != null) detached = true
    }

    /**
     * Takes the device back while the host is on screen. The screen that held it lets go as it leaves (after the
     * navigation fade, longer under a system animator scale), so a busy engine is retried for a while; a host that
     * goes away again ends the attempts.
     */
    private suspend fun attachTransport() {
        var attempts = 0
        while (hostVisible && opened != null && !transportOpen) {
            if (bindTransport()) return
            if (++attempts >= ATTACH_ATTEMPTS) return
            delay(ATTACH_RETRY_MS)
        }
    }

    /**
     * Opens the engine's transport for the open project and gives it everything a fresh transport forgets (tempo,
     * master, arrangement, click) and the stopped position; true when the transport is open and bound. One open at a
     * time, and never cancelled once begun: a leave that lands while it runs closes it again as soon as it returns.
     * False when the host is hidden, the device is busy or it failed.
     */
    private suspend fun bindTransport(): Boolean = transportMutex.withLock {
        withContext(NonCancellable) {
            if (transportOpen) return@withContext true // bound when it was opened
            if (!openTransportLocked()) return@withContext false
            detached = false
            val project = history?.present ?: return@withContext true
            pushEngine(project, _state.value.metronomeOn)
            // Play may already have moved the state on from Stopped; either way the engine resumes where the user left.
            when (val playback = _state.value.playback) {
                is PlaybackState.Stopped -> audio.seek(playback.positionBeats)
                is PlaybackState.Starting -> audio.seek(playback.positionBeats)
                PlaybackState.Playing, PlaybackState.Pausing -> Unit // never with a transport opened a moment ago
            }
            true
        }
    }

    /**
     * The transport open while the host shows, under [transportMutex]: true when it is open afterwards. A host that
     * went away while the engine opened gets the device closed again at once; [transportOpen] only ever turns true here.
     */
    private suspend fun openTransportLocked(): Boolean {
        if (transportOpen) return true
        if (!hostVisible) return false
        if (audio.openTransport() != TransportOpenResult.Opened) return false
        if (!hostVisible) {
            audio.closeTransport()
            return false
        }
        transportOpen = true
        return true
    }

    /** Everything the engine's transport must hold for [project], in the contract's order. */
    private fun pushEngine(project: Project, metronomeOn: Boolean) {
        audio.setTempo(project.tempo, project.timeSignature)
        audio.setMaster(project.masterVolume.toLinear())
        pushArrangement(project)
        bridge.apply(settings, project.tempo, project.timeSignature)
        bridge.setEnabled(metronomeOn)
    }

    // --- Open and close ----------------------------------------------------------------------------------------------

    private suspend fun install(id: ProjectId, opened: OpenedProject) {
        val project = opened.project
        // A tap on Tuner or Metronome during the start-up open leaves the device to that screen; the attach takes it back.
        transportMutex.withLock { withContext(NonCancellable) { openTransportLocked() } }
        val missing = LinkedHashSet(opened.missingAssets)
        for (assetId in project.assets.keys) {
            if (assetId in missing) continue
            val path = opened.audioPath(assetId)
            if (path == null || !sources.open(assetId, path)) missing += assetId
        }
        if (transportOpen) pushEngine(project, project.metronomeOn)
        val started = EditHistory.start(project)
        history = started
        this.opened = opened
        detached = !transportOpen
        lastEdit = null
        referencedAssets = project.assets.keys
        pendingRetired.clear()
        val viewState = repository.viewState(id)
        _state.value = SessionState(
            project = project,
            viewState = viewState,
            missingAssets = missing.toPersistentSet(),
            metronomeOn = project.metronomeOn,
        )
        for (assetId in project.assets.keys) {
            if (assetId in missing) continue
            opened.overviewPath(assetId)?.let { overviews.track(id, assetId, it) }
        }
        repository.setOpen(id)
        if (missing.isNotEmpty()) _messages.trySend(SessionMessage.AssetsMissing(missing.size))
        // The host may have gone and come back while the sources opened: take the device back now.
        if (hostVisible) launchAttach()
    }

    private suspend fun closeCurrent() {
        overviews.clear()
        attachJob?.cancel()
        attachJob = null
        detached = false
        transportMutex.withLock { // after an open in flight, so the device never stays with a closed project
            if (transportOpen) {
                audio.closeTransport() // before the sources: the engine refuses to close a source its arrangement still references
                transportOpen = false
            }
        }
        sources.closeAll()
        history = null
        opened = null
        lastEdit = null
        referencedAssets = emptySet()
        settingsDraft.value = null
    }

    private fun publishUnavailable(reason: String) {
        _state.value = SessionState(unavailable = reason)
        _messages.trySend(SessionMessage.DocumentUnavailable(reason))
    }

    // --- Engine binding ----------------------------------------------------------------------------------------------

    /** The smallest engine update for a settled [edit] (contracts/project-session.md "Engine binding rules"). */
    private fun applyToEngine(project: Project, edit: ProjectEdit) {
        when (edit) {
            is SetGain -> pushTrack(project, edit.id)
            is SetMuted -> pushTrack(project, edit.id)
            is SetSoloed -> project.tracks.indices.forEach { pushTrack(project, it) }
            is SetTempo, is SetTimeSignature -> pushTempo(project)
            is SetMasterVolume -> audio.setMaster(project.masterVolume.toLinear())
            is RenameProject, is SetDescription, is SetKey, is RenameTrack, is SetLabel -> Unit
            is AddTrack, is MoveTrack, is DeleteTrack, is ImportClips, is SplitClip, is MoveClip, is DeleteClip -> pushArrangement(project)
        }
    }

    private fun pushTrack(project: Project, id: TrackId) {
        val index = project.tracks.indexOfFirst { it.id == id }
        if (index >= 0) pushTrack(project, index)
    }

    private fun pushTrack(project: Project, index: Int) {
        val render = ArrangementMapper.track(project, index)
        audio.setTrack(index, render.gainLinear, render.audible)
    }

    private fun pushTempo(project: Project) {
        audio.setTempo(project.tempo, project.timeSignature)
        pushArrangement(project)
        bridge.apply(settings, project.tempo, project.timeSignature)
    }

    private fun pushArrangement(project: Project) {
        when (val result = ArrangementMapper.map(project, sources.handles)) {
            is ArrangementResult.Mapped -> audio.setArrangement(result.snapshot)
            ArrangementResult.TooLarge -> _messages.trySend(SessionMessage.ProjectTooLarge)
        }
    }

    /** Undo and redo re-push what differs from [before] (the metronome toggle stays as it is: it is not history). */
    private fun restore(next: EditHistory, before: Project) {
        val project = next.present.copy(metronomeOn = before.metronomeOn)
        val restored = next.copy(present = project)
        lastEdit = null
        history = restored
        if (project.tempo != before.tempo || project.timeSignature != before.timeSignature) {
            audio.setTempo(project.tempo, project.timeSignature)
            bridge.apply(settings, project.tempo, project.timeSignature)
        }
        if (project.masterVolume != before.masterVolume) audio.setMaster(project.masterVolume.toLinear())
        pushArrangement(project)
        settle(restored)
    }

    /** After the history changed: retire what no snapshot references, schedule the save, publish. */
    private fun settle(next: EditHistory) {
        retireUnreferenced(next)
        autosave.onEdit()
        val project = next.present
        _state.update { it.copy(project = project, canUndo = next.canUndo, canRedo = next.canRedo, metronomeOn = project.metronomeOn) }
    }

    private fun retireUnreferenced(next: EditHistory) {
        val referenced = HashSet<AudioAssetId>()
        for (snapshot in next.snapshots) referenced += snapshot.assets.keys
        for (assetId in referencedAssets) {
            if (assetId in referenced) continue
            retire(assetId)
        }
        referencedAssets = referenced
    }

    private fun retire(assetId: AudioAssetId) {
        sources.close(assetId)
        overviews.stop(assetId)
        pendingRetired += assetId
        _state.update { it.copy(assetsReady = it.assetsReady.removing(assetId), overviews = it.overviews.removing(assetId), missingAssets = it.missingAssets.removing(assetId)) }
    }

    private fun coalesces(edit: ProjectEdit, last: ProjectEdit?): Boolean = when (edit) {
        is SetGain -> last is SetGain && last.id == edit.id
        is SetMasterVolume -> last is SetMasterVolume
        is SetTempo -> last is SetTempo
        else -> false
    }

    // --- Playback policy ---------------------------------------------------------------------------------------------

    private fun dispatch(event: PlaybackEvent) {
        val before = _state.value.playback
        val outcome = PlaybackPolicy.onEvent(before, event)
        _state.update { it.copy(playback = outcome.state) }
        when (val transition = outcome.transition) {
            PlaybackTransition.Start -> if (startJob?.isActive != true) startJob = scope.launch(CoroutineName("startPlayback")) { startPlayback() }
            PlaybackTransition.BecomePlaying -> Unit
            PlaybackTransition.StopWithMessage -> {
                host.release()
                _messages.trySend(SessionMessage.StartFailed)
            }
            PlaybackTransition.Pause -> stopEngine(before)
            is PlaybackTransition.Seek -> seekRequests.trySend(transition.beats)
            PlaybackTransition.None -> Unit
        }
    }

    /** Runs once per Start; a pause that lands while it waits for the transport ends it before the engine plays. */
    private suspend fun startPlayback() {
        attachJob?.join() // play right after returning to the Timeline waits for the transport to be back
        if (_state.value.playback !is PlaybackState.Starting) return
        val project = history?.present
        val result = PerfTrace.section(PerfSection.TRANSPORT_PLAY) {
            if (project == null || !bindTransport()) {
                PlayResult.Failed(NO_TRANSPORT)
            } else if (_state.value.playback !is PlaybackState.Starting) {
                return@section null
            } else {
                host.keepAlive(PlaybackNotification(TransportCopy.playing, project.name))
                audio.play()
            }
        }
        when (result) {
            null -> Unit // paused while the transport was being bound: nothing started
            PlayResult.Started -> if (_state.value.playback is PlaybackState.Starting) {
                dispatch(PlaybackEvent.Started)
            } else {
                audio.pause()
                host.release()
            }
            is PlayResult.Failed -> dispatch(PlaybackEvent.Failed)
        }
    }

    /**
     * Pause is synchronous in the seam: the engine stops at its next block and the position is final at once. From
     * [PlaybackState.Starting] the engine has not taken the stored position yet, so that position stays.
     */
    private fun stopEngine(before: PlaybackState) {
        audio.pause()
        host.release()
        val position = if (before is PlaybackState.Starting) before.positionBeats else readEnginePosition()
        dispatch(PlaybackEvent.Paused(position))
    }

    private fun readEnginePosition(): Beats {
        audio.readTransport(positionSnapshot)
        return Beats(positionSnapshot.positionBeats)
    }

    private fun readFrame(frameNanos: Long, into: TransportSnapshot) {
        if (frameNanos != lastFrameNanos) {
            lastFrameNanos = frameNanos
            audio.readTransport(frameSnapshot)
        }
        into.copyFrom(frameSnapshot)
    }

    // --- Observers ---------------------------------------------------------------------------------------------------

    private suspend fun observeEngineEvents() {
        audio.events.collect { event ->
            when (event) {
                AudioEvent.Interrupted -> dispatch(PlaybackEvent.Interrupted)
                AudioEvent.InterruptionEnded -> dispatch(PlaybackEvent.InterruptionEnded)
                AudioEvent.DeviceLost, AudioEvent.OutputRouteLost -> dispatch(PlaybackEvent.RouteLost)
            }
        }
    }

    private suspend fun observeMetronomePreferences() {
        preferences.snapshot.collect { snapshot ->
            if (snapshot.settings == settings) return@collect
            settings = snapshot.settings
            // Not while detached: the shared click renderer then belongs to the standalone metronome; the attach re-applies.
            if (transportOpen) history?.present?.let { bridge.apply(settings, it.tempo, it.timeSignature) }
        }
    }

    private suspend fun observeHostStopRequests() {
        host.stopRequests.collect { dispatch(PlaybackEvent.HostStop) }
    }

    /** Seeks are conflated: a scrub lands on the latest position and never queues the engine's re-prime. */
    private suspend fun observeSeekRequests() {
        for (beats in seekRequests) {
            // While another screen holds the device the stored position moves alone; the bind pushes it on return.
            // Under the transport mutex, so a scrub never overtakes the bind's own seek.
            transportMutex.withLock { if (transportOpen) PerfTrace.section(PerfSection.TRANSPORT_SEEK) { audio.seek(beats) } }
        }
    }

    private fun onOverview(assetId: AudioAssetId, status: OverviewStatus, overview: WaveformOverview?) {
        _state.update { current ->
            if (current.project?.assets?.containsKey(assetId) != true) return@update current
            current.copy(
                assetsReady = current.assetsReady.putting(assetId, status),
                overviews = if (overview == null) current.overviews else current.overviews.putting(assetId, overview),
            )
        }
    }

    // --- Save and import ---------------------------------------------------------------------------------------------

    private suspend fun persist() {
        val project = history?.present ?: return
        val retired = pendingRetired.toSet()
        pendingRetired.clear()
        _state.update { it.copy(saving = true) }
        try {
            PerfTrace.section(PerfSection.TIMELINE_PROJECT_SAVE) { repository.save(project, retired) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            pendingRetired += retired
            _messages.trySend(SessionMessage.SaveFailed)
        } finally {
            _state.update { it.copy(saving = false) }
        }
    }

    private suspend fun importNow(projectId: ProjectId, files: List<PickedFile>, target: ImportTarget) = importMutex.withLock {
        val accepted = ArrayList<ImportResult.Accepted>()
        val refused = ArrayList<Pair<String, String>>()
        for (file in files) {
            when (val result = repository.importAudio(projectId, file)) {
                is ImportResult.Accepted -> accepted += result
                is ImportResult.Refused -> refused += result.fileName to refusalText(result.reason)
            }
        }
        if (refused.isNotEmpty()) _messages.trySend(SessionMessage.ImportRefused(refused))
        if (accepted.isEmpty() || history?.present?.id != projectId) return@withLock
        val playable = accepted.filter { sources.open(it.assetId, it.audioPath) }
        val assets = accepted.associate { it.assetId to it.asset }
        val edit = when (target) {
            is ImportTarget.Track -> ImportToTrack(target.trackId, accepted.map { it.clip.toClip(IdGenerator.clipId(), target.atBeats) }, assets)
            is ImportTarget.NewTracks -> ImportAsNewTracks(
                accepted.map { NewTrackWithClip(IdGenerator.trackId(), ClipTemplate.nameOf(it.asset.fileName), it.clip.toClip(IdGenerator.clipId(), target.atBeats)) },
                assets,
            )
        }
        closeEditGroup()
        val rejection = edit(edit)
        if (rejection != null) {
            for (result in accepted) {
                sources.close(result.assetId)
                pendingRetired += result.assetId
            }
            autosave.onEdit()
            _messages.trySend(SessionMessage.EditRejected(rejection))
            return@withLock
        }
        val unplayable = accepted.map { it.assetId }.toSet() - playable.map { it.assetId }.toSet()
        if (unplayable.isNotEmpty()) {
            _state.update { it.copy(missingAssets = it.missingAssets.addingAll(unplayable)) }
            _messages.trySend(SessionMessage.AssetsMissing(unplayable.size))
        }
        for (result in playable) overviews.track(projectId, result.assetId, result.overviewPath)
    }

    private fun refusalText(reason: ImportRefusal): String = when (reason) {
        ImportRefusal.UnsupportedFormat -> "not a WAV, FLAC or MP3 file"
        ImportRefusal.TooManyChannels -> "more than two channels"
        ImportRefusal.TooLong -> "longer than 30 minutes"
        ImportRefusal.TooLarge -> "larger than 500 MB"
        ImportRefusal.Unreadable -> "could not be read"
    }

    private companion object {
        const val NOT_FOUND = "The project could not be found"
        /** `PlayResult.Failed` code when the transport could not be opened (the engine was busy or its device failed). */
        const val NO_TRANSPORT = -1
    }
}

/** Taking the device back: the previous screen's stream closes as it leaves (after the navigation fade, longer under an
 * animator scale), so the open is retried for up to five seconds. */
private const val ATTACH_ATTEMPTS = 50
private const val ATTACH_RETRY_MS = 100L
