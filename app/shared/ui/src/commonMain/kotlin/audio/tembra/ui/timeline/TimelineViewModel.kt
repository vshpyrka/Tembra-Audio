package audio.tembra.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.TapTempoSequence
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AddTrack
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.DeleteClip
import audio.tembra.core.project.DeleteTrack
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.MoveClip
import audio.tembra.core.project.MoveDirection
import audio.tembra.core.project.MoveTrack
import audio.tembra.core.project.Percent
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectEdit
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetMuted
import audio.tembra.core.project.SetSoloed
import audio.tembra.core.project.SetTempo
import audio.tembra.core.project.SetTimeSignature
import audio.tembra.core.project.SplitClip
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.ZoomLevel
import audio.tembra.core.project.audibleTracks
import audio.tembra.core.project.clipLengthBeats
import audio.tembra.core.project.findClip
import audio.tembra.core.project.findTrack
import audio.tembra.core.project.lengthBeats
import audio.tembra.core.project.lengthSeconds
import audio.tembra.core.project.tempoMap
import audio.tembra.core.project.trackOf
import audio.tembra.core.timeline.Snap
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.ProjectViewState
import audio.tembra.data.timeline.TimelinePreferences
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.WindowControls
import audio.tembra.ui.platform.AudioPickRequest
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.platform.AudioPickResult
import audio.tembra.ui.session.ImportTarget
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.session.SessionMessage
import audio.tembra.ui.session.SessionState
import audio.tembra.ui.session.TransportTelemetry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * The Timeline's state holder (contracts/timeline-screen.md; Constitution II): maps the session's project and the
 * view state it owns ([TimelineViewState]) to one [TimelineUiState], and turns every intent into a session call or a
 * view-state update. The playhead per frame is read in draw lambdas through [telemetry]; the 10 Hz mirror for
 * semantics comes from [pollPosition]. Named `viewModelScope` launches: [observeProject], [pollPosition],
 * `pickAudio`, `saveViewState`; all but the pickers are tied to the screen being visible ([screenVisible]).
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class TimelineViewModel(
    private val session: ProjectSession,
    private val picker: AudioFilePicker,
    private val preferences: MetronomePreferences,
    private val timelinePreferences: TimelinePreferences,
    private val windowControls: WindowControls,
) : ViewModel() {

    private val view = MutableStateFlow(TimelineViewState())

    /** Whether this device shows the overview strip (spec FR-015; T151): outside [view], so opening another project keeps it. */
    private val overviewStripShown = MutableStateFlow(true)

    /** The last global metronome settings the preferences emitted: outside [view] for the same reason (T147). */
    private val metronomeSettings = MutableStateFlow(MetronomeSettings())

    /** The menu's full-screen row from the platform's window controls (feature 011, spec FR-010a). */
    private val fullScreenItem = combine(windowControls.fullScreenAvailable, windowControls.isFullScreen, ::fullScreenItemOf)

    val uiState: StateFlow<TimelineUiState> = combine(session.state, view, overviewStripShown, metronomeSettings, fullScreenItem, ::mapState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentUi())

    /** One-shot snackbar messages: the session's, mapped, merged with the ViewModel's own, to the one collector. */
    private val local = Channel<TimelineMessage>(Channel.BUFFERED)
    val messages: Flow<TimelineMessage> = merge(session.messages.map(::toMessage), local.receiveAsFlow())

    /** Draw-phase handle: the session reads the engine at most once per frame. */
    val telemetry: TransportTelemetry get() = session.transportTelemetry

    private val snapshot = TransportSnapshot()
    private var observeProjectJob: Job? = null
    private var observePreferencesJob: Job? = null
    private var tapWindowJob: Job? = null
    private var pollJob: Job? = null
    private var saveViewStateJob: Job? = null
    private var resumeAfterScrub = false

    // --- Visibility --------------------------------------------------------------------------------------------------

    /** The Timeline is shown or left; leaving ends the gain-drag undo group and the playhead poll, never playback. */
    fun screenVisible(visible: Boolean) {
        if (visible) {
            if (observeProjectJob == null) observeProjectJob = viewModelScope.launch(CoroutineName("observeProject")) { observeProject() }
            if (pollJob == null) pollJob = viewModelScope.launch(CoroutineName("pollPosition")) { pollPosition() }
            if (observePreferencesJob == null) observePreferencesJob = viewModelScope.launch(CoroutineName("observePreferences")) { observePreferences() }
        } else {
            observeProjectJob?.cancel()
            observeProjectJob = null
            observePreferencesJob?.cancel()
            observePreferencesJob = null
            pollJob?.cancel()
            pollJob = null
            session.closeEditGroup()
        }
    }

    fun flushSave() = session.flushSave()

    // --- Transport ---------------------------------------------------------------------------------------------------

    fun playPause() {
        if (session.isPlaying) {
            session.pause()
        } else {
            view.update { it.copy(following = true) }
            session.play()
        }
    }

    fun skipToStart() {
        view.update { it.copy(following = true, positionBeats = Beats.Zero, positionSeconds = 0.0, viewport = it.viewport.scrollTo(Beats.Zero)) }
        session.skipToStart()
    }

    fun stepBar(delta: Int) = session.stepBar(delta)

    /** A ruler tap: snapped to the grid, and the view follows the playhead again (spec FR-012). */
    fun seek(beats: Beats) {
        val target = snapped(beats)
        view.update { it.copy(following = true, positionBeats = target, viewport = it.viewport.follow(target)) }
        session.seek(target)
    }

    /** A ruler drag (clarification 4): playback pauses for the drag and resumes from the release point. */
    fun scrubStart() {
        resumeAfterScrub = session.isPlaying
        if (resumeAfterScrub) session.pause()
    }

    /** The marker under the finger, snapped to the grid when it is on (spec FR-011, D1). */
    fun scrub(beats: Beats) {
        val target = snapped(beats)
        view.update { it.copy(positionBeats = target) }
        session.seek(target)
    }

    /** The release point, snapped like the drag; playback resumes from it if the drag paused it. */
    fun scrubEnd(beats: Beats) {
        val target = snapped(beats)
        view.update { it.copy(positionBeats = target, following = true) }
        session.seek(target)
        if (resumeAfterScrub) {
            resumeAfterScrub = false
            session.play()
        }
    }

    fun setMasterVolume(percent: Int) = edit(SetMasterVolume(Percent.clamped(percent)))

    fun toggleMetronome() = session.setMetronome(!session.state.value.metronomeOn)

    // --- Viewport ----------------------------------------------------------------------------------------------------

    /** The lane area's width in pixels and the density; the first measure fits the project or restores the stored zoom. */
    fun viewportMeasured(widthPx: Double, densityScale: Double) {
        if (widthPx <= 0.0) return
        view.update { current ->
            val port = current.viewport
            if (port.widthPx == widthPx && port.densityScale == densityScale) return@update current
            val measured = port.copy(widthPx = widthPx, densityScale = densityScale)
            val next = if (port.widthPx <= 0.0) initialZoom(measured) else measured.withZoom(measured.zoom)
            current.copy(viewport = next)
        }
    }

    fun zoomAround(centreX: Double, factor: Double) {
        updateViewport { it.zoomAround(centreX, factor) }
        scheduleViewStateSave()
    }

    /** One step is a factor of √2; two steps double the zoom. */
    fun zoomStep(steps: Int) {
        updateViewport { it.zoomAround(it.widthPx / 2, STEP_FACTOR.pow(steps)) }
        scheduleViewStateSave()
    }

    fun fitProject() {
        updateViewport { it.fit() }
        scheduleViewStateSave()
    }

    /** A manual scroll suspends following until play, skip-to-start or a ruler tap (spec FR-012). */
    fun scrollBy(px: Double) {
        view.update { it.copy(following = false, viewport = it.viewport.scrollBy(px)) }
    }

    /** The overview strip's window dragged (spec FR-015, D1): [beats] at the left edge, a manual scroll like [scrollBy]. */
    fun scrollTo(beats: Beats) {
        view.update { it.copy(following = false, viewport = it.viewport.scrollTo(beats)) }
    }

    /**
     * The overview strip's edge dragged (spec FR-015, D1): the span from [startBeats] to [endBeats] fills the lanes,
     * the zoom clamped to the widest and closest levels and [startBeats] kept at the left edge; an empty span is ignored.
     */
    fun zoomTo(startBeats: Beats, endBeats: Beats) {
        val span = endBeats.value - startBeats.value
        if (span <= 0.0) return
        view.update { it.copy(following = false) }
        updateViewport { port ->
            if (port.widthPx <= 0.0) port else port.withZoom(ZoomLevel(port.widthPx / (span * port.densityScale))).scrollTo(startBeats)
        }
        scheduleViewStateSave()
    }

    fun setGrid(grid: GridSize) {
        view.update { it.copy(grid = grid) }
        scheduleViewStateSave()
    }

    /** The overflow menu's "Overview strip" entry (spec FR-015; T151): shown or hidden at once, remembered on the device. */
    /** The menu's "Full screen" (true) or "Exit full screen" (false): the platform resizes the window (feature 011). */
    fun setFullScreen(fullScreen: Boolean) {
        if (fullScreen) windowControls.enterFullScreen() else windowControls.exitFullScreen()
    }

    fun setOverviewStripShown(shown: Boolean) {
        overviewStripShown.value = shown
        viewModelScope.launch(CoroutineName("setOverviewStripShown")) { timelinePreferences.setOverviewStripShown(shown) }
    }

    // --- Tracks ------------------------------------------------------------------------------------------------------

    /** A new track at the bottom, selected so the next "Add audio…" lands on it (spec FR-019). */
    fun addTrack() {
        val add = AddTrack()
        if (edit(add)) view.update { it.copy(selection = Selection.TrackSelected(add.id)) }
    }

    fun selectTrack(id: TrackId) = view.update { it.copy(selection = Selection.TrackSelected(id)) }

    fun toggleMute(id: TrackId) {
        val track = session.state.value.project?.findTrack(id) ?: return
        edit(SetMuted(id, !track.muted))
    }

    fun toggleSolo(id: TrackId) {
        val track = session.state.value.project?.findTrack(id) ?: return
        edit(SetSoloed(id, !track.soloed))
    }

    fun setGain(id: TrackId, gainDb: GainDb) = edit(SetGain(id, gainDb))

    fun moveTrack(id: TrackId, delta: Int) {
        if (delta == 0) return
        edit(MoveTrack(id, if (delta < 0) MoveDirection.Up else MoveDirection.Down))
    }

    fun requestDeleteTrack(id: TrackId) = view.update { it.copy(confirmDelete = id) }

    fun confirmDeleteTrack() {
        val id = view.value.confirmDelete ?: return
        view.update { it.copy(confirmDelete = null, selection = if (it.selection.trackIdOrNull == id) Selection.None else it.selection, expandedTrack = it.expandedTrack.takeUnless { track -> track == id }) }
        edit(DeleteTrack(id))
    }

    fun dismissDeleteTrack() = view.update { it.copy(confirmDelete = null) }

    /** The expanded layout's inline settings row: open for [id], closed for null (spec FR-041). */
    fun expandTrack(id: TrackId?) = view.update { it.copy(expandedTrack = id) }

    /** One file onto [trackId] or the selected track; with no selection the toolbar's entry behaves as "as new track" (spec FR-043). */
    fun addAudio(trackId: TrackId?) {
        val target = trackId ?: currentUi().selection.trackIdOrNull
        pickAudio(if (target == null) ImportTarget.NewTracks(insertionPoint()) else ImportTarget.Track(target, insertionPoint()))
    }

    fun addAudioAsNewTrack() = pickAudio(ImportTarget.NewTracks(insertionPoint()))

    // --- Clips -------------------------------------------------------------------------------------------------------

    fun selectClip(trackId: TrackId, clipId: ClipId) = view.update { it.copy(selection = Selection.ClipSelected(trackId, clipId)) }

    fun clearSelection() = view.update { it.copy(selection = Selection.None) }

    /** Splits the selected clip at the playhead (spec FR-032); the new right half becomes the selection. */
    fun split() {
        val state = currentUi()
        val clip = state.selectedClip ?: return
        val trackId = state.selection.trackIdOrNull ?: return
        if (!state.canSplit) return
        val split = SplitClip(clip.id, Snap.snap(state.positionBeats, state.grid, state.timeSignature))
        PerfTrace.section(PerfSection.TIMELINE_CLIP_SPLIT) {
            if (edit(split)) view.update { it.copy(selection = Selection.ClipSelected(trackId, split.rightId)) }
        }
    }

    fun moveClip(clipId: ClipId, toTrack: TrackId, startBeats: Beats) {
        val state = currentUi()
        val target = Snap.snap(startBeats, state.grid, state.timeSignature)
        if (edit(MoveClip(clipId, toTrack, target))) view.update { it.copy(selection = Selection.ClipSelected(toTrack, clipId)) }
    }

    /** A long press (touch) or a press past the slop (pointer) lifts the clip; it is selected and follows the finger (spec FR-033). */
    fun clipDragStart(clipId: ClipId) {
        val project = session.state.value.project ?: return
        val track = project.trackOf(clipId) ?: return
        val clip = project.findClip(clipId) ?: return
        session.closeEditGroup()
        view.update { it.copy(selection = Selection.ClipSelected(track.id, clipId), drag = ClipDragState(clipId, track.id, clip.startBeats)) }
    }

    fun clipDrag(clipId: ClipId, deltaBeats: Double, deltaTracks: Int) {
        view.update { current ->
            val drag = current.drag
            if (drag == null || drag.clipId != clipId) current else current.copy(drag = drag.copy(deltaBeats = deltaBeats, deltaTracks = deltaTracks))
        }
    }

    /** Release: one MoveClip with the snapped start and the target track; an unchanged or refused drop leaves the clip where it was. */
    fun clipDragEnd() {
        val drag = view.value.drag ?: return
        view.update { it.copy(drag = null) }
        val project = session.state.value.project ?: return
        val target = dropTarget(project, drag) ?: return
        if (target.first == drag.originTrack && target.second == drag.originStart) return
        moveClip(drag.clipId, target.first, target.second)
    }

    fun clipDragCancel() = view.update { it.copy(drag = null) }

    fun deleteClip(clipId: ClipId) {
        if (edit(DeleteClip(clipId))) view.update { current -> current.copy(selection = current.selection.takeUnless { it is Selection.ClipSelected && it.clipId == clipId } ?: Selection.None) }
    }

    fun undo() = session.undo()

    fun redo() = session.redo()

    // --- Tempo, signature, sheets ------------------------------------------------------------------------------------

    fun openSheet(sheet: TimelineSheet?) = view.update { it.copy(sheet = sheet) }

    /** The tempo dialog's live drag: the project follows at once so the ruler and the click show it (spec FR-035). */
    fun tempoDragging(tempo: Tempo) = edit(SetTempo(tempo))

    fun tempoCommit(tempo: Tempo) {
        edit(SetTempo(tempo))
        session.closeEditGroup()
    }

    fun setTimeSignature(signature: TimeSignature) = edit(SetTimeSignature(signature))

    /** A tap in the tempo dialog: the run's average becomes the tempo at once; the run ends two seconds after the last tap, as one undo step. */
    fun tapTempo(atNanos: Long) {
        val sequence = view.value.tap.tap(atNanos)
        val counted = when (val previous = view.value.tapCaption) {
            TapCaption.Rest -> 0
            TapCaption.Again -> 1
            is TapCaption.Counting -> previous.taps
        }
        val caption = if (sequence.count < 2) TapCaption.Again else TapCaption.Counting(counted + 1)
        view.update { it.copy(tap = sequence, tapCaption = caption) }
        sequence.tempo?.let { edit(SetTempo(it)) }
        tapWindowJob?.cancel()
        tapWindowJob = viewModelScope.launch(CoroutineName("tapWindow")) {
            delay(TapTempoSequence.WINDOW_NANOS / 1_000_000)
            view.update { it.copy(tap = TapTempoSequence(), tapCaption = TapCaption.Rest) }
            session.closeEditGroup()
        }
    }

    // --- Metronome settings sheet: the global metronome's preferences (spec FR-038, FR-039) ----------------------------

    fun selectClickSound(sound: ClickSound) = updateMetronome { it.copy(sound = sound) }

    fun selectSubdivision(subdivision: Subdivision) = updateMetronome { it.copy(subdivision = subdivision) }

    /** Cycles one accent cell of the pattern laid over the project's signature; a hand-edited pattern has no named style. */
    fun cycleBeat(index: Int) {
        val signature = session.state.value.project?.timeSignature ?: return
        updateMetronome { settings ->
            val laid = settings.style?.pattern(signature) ?: settings.pattern.resizedTo(signature.beats)
            if (index !in laid.levels.indices) return@updateMetronome settings
            val pattern = laid.cycle(index)
            settings.copy(pattern = pattern, style = MetronomeStyle.matching(pattern, signature))
        }
    }

    fun selectStyle(style: MetronomeStyle) {
        val signature = session.state.value.project?.timeSignature ?: return
        updateMetronome { it.copy(style = style, pattern = style.pattern(signature)) }
    }

    fun importCustomSound() {
        local.trySend(TimelineMessage.CustomSoundFromMetronome)
    }

    // --- Mapping -----------------------------------------------------------------------------------------------------

    private fun mapState(
        state: SessionState,
        view: TimelineViewState,
        overviewStripShown: Boolean,
        metronomeSettings: MetronomeSettings,
        fullScreenItem: FullScreenItem,
    ): TimelineUiState {
        val project = state.project ?: return TimelineUiState(
            loaded = state.unavailable != null,
            playback = state.playback,
            overviewStripShown = overviewStripShown,
            fullScreenItem = fullScreenItem,
            metronomeSettings = metronomeSettings,
        )
        val tempoMap = project.tempoMap
        val audible = project.audibleTracks
        val tracks = project.tracks.map { track ->
            TrackUi(
                id = track.id,
                name = track.name,
                label = track.label,
                colorIndex = track.colorIndex,
                gainDb = track.gainDb,
                muted = track.muted,
                soloed = track.soloed,
                audible = track.id in audible,
                clips = track.clips.map { clip ->
                    val asset = project.assets[clip.assetId]
                    val available = asset?.isAvailable == true && clip.assetId !in state.missingAssets
                    ClipUi(
                        id = clip.id,
                        assetId = clip.assetId,
                        name = clip.name,
                        startBeats = clip.startBeats,
                        lengthBeats = project.clipLengthBeats(clip),
                        offsetFrames = clip.offsetFrames,
                        lengthFrames = clip.lengthFrames,
                        sampleRate = asset?.sampleRate ?: DEFAULT_SAMPLE_RATE,
                        overview = state.overviews[clip.assetId],
                        overviewStatus = state.assetsReady[clip.assetId] ?: if (available) OverviewStatus.Building(0f) else OverviewStatus.Failed,
                        available = available,
                    )
                }.toPersistentList(),
            )
        }.toPersistentList()
        val lengthBeats = project.lengthBeats
        val viewport = view.viewport.copy(projectLengthBeats = lengthBeats, beatsPerBar = project.timeSignature.beats)
        val playing = state.isPlaying
        val positionBeats = when (val playback = state.playback) {
            is PlaybackState.Stopped -> playback.positionBeats
            is PlaybackState.Starting -> playback.positionBeats
            PlaybackState.Playing, PlaybackState.Pausing -> view.positionBeats
        }
        val positionSeconds = if (playing) view.positionSeconds else tempoMap.beatsToSeconds(positionBeats)
        val selection = view.selection.validated(project)
        val selectedClip = (selection as? Selection.ClipSelected)?.let { project.findClip(it.clipId) }
        val grid = view.grid ?: state.viewState.grid
        // The scissors cut at the playhead snapped to the grid (spec FR-032), so availability asks about that point.
        val canSplit = selectedClip != null && run {
            val start = tempoMap.beatsToSeconds(selectedClip.startBeats)
            val end = tempoMap.beatsToSeconds(selectedClip.startBeats + project.clipLengthBeats(selectedClip))
            val at = tempoMap.beatsToSeconds(Snap.snap(positionBeats, grid, project.timeSignature))
            at - start > ProjectLimits.MIN_SPLIT_EDGE_SECONDS && end - at > ProjectLimits.MIN_SPLIT_EDGE_SECONDS
        }
        val ready = tracks.all { track -> track.clips.all { !it.available || it.overviewStatus !is OverviewStatus.Building } }
        return TimelineUiState(
            loaded = true,
            projectName = project.name,
            tempo = project.tempo,
            timeSignature = project.timeSignature,
            tracks = tracks,
            lengthBeats = lengthBeats,
            lengthSeconds = project.lengthSeconds,
            selection = selection,
            viewport = viewport,
            grid = grid,
            overviewStripShown = overviewStripShown,
            fullScreenItem = fullScreenItem,
            following = view.following,
            playback = state.playback,
            positionBeats = positionBeats,
            positionSeconds = positionSeconds,
            metronomeOn = state.metronomeOn,
            masterVolume = project.masterVolume.value,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            canSplit = canSplit,
            expandedTrack = view.expandedTrack?.takeIf { project.findTrack(it) != null },
            sheet = view.sheet,
            isFirstContentReady = ready,
            missingAssets = state.missingAssets.size,
            confirmDelete = view.confirmDelete?.takeIf { project.findTrack(it) != null },
            metronomeSettings = metronomeSettings,
            tapCaption = view.tapCaption,
            drag = view.drag?.let { drag ->
                val clip = project.findClip(drag.clipId) ?: return@let null
                val target = dropTarget(project, drag) ?: return@let null
                ClipDragUi(drag.clipId, drag.originTrack, target.first, target.second, project.clipLengthBeats(clip), clip.name)
            },
        )
    }

    /** Where a drag would drop: the row offset clamped to the tracks, the start snapped to the grid and never before zero. */
    private fun dropTarget(project: Project, drag: ClipDragState): Pair<TrackId, Beats>? {
        val originIndex = project.tracks.indexOfFirst { it.id == drag.originTrack }
        if (originIndex < 0) return null
        val track = project.tracks[(originIndex + drag.deltaTracks).coerceIn(0, project.tracks.lastIndex)]
        val grid = view.value.grid ?: session.state.value.viewState.grid
        val start = Snap.snap(Beats(drag.originStart.value + drag.deltaBeats).coerceAtLeastZero(), grid, project.timeSignature)
        return track.id to start
    }

    private fun Selection.validated(project: Project): Selection = when (this) {
        Selection.None -> this
        is Selection.TrackSelected -> if (project.findTrack(trackId) != null) this else Selection.None
        is Selection.ClipSelected -> if (project.findTrack(trackId)?.clips?.any { it.id == clipId } == true) this else Selection.None
    }

    private fun toMessage(message: SessionMessage): TimelineMessage = when (message) {
        is SessionMessage.ImportRefused -> TimelineMessage.ImportRefused(message.files)
        SessionMessage.StartFailed -> TimelineMessage.StartFailed
        is SessionMessage.DocumentUnavailable -> TimelineMessage.DocumentUnavailable(message.reason)
        is SessionMessage.AssetsMissing -> TimelineMessage.AssetsMissing(message.count)
        SessionMessage.SaveFailed -> TimelineMessage.SaveFailed
        SessionMessage.ProjectTooLarge -> TimelineMessage.ProjectTooLarge
        is SessionMessage.EditRejected -> TimelineMessage.EditRejected(message.reason)
    }

    // --- Helpers -----------------------------------------------------------------------------------------------------

    /** The state an intent acts on, mapped now from the sources of truth: [uiState] is derived asynchronously. */
    private fun currentUi(): TimelineUiState = mapState(
        session.state.value,
        view.value,
        overviewStripShown.value,
        metronomeSettings.value,
        fullScreenItemOf(windowControls.fullScreenAvailable.value, windowControls.isFullScreen.value),
    )

    private fun fullScreenItemOf(available: Boolean, fullScreen: Boolean): FullScreenItem = when {
        !available -> FullScreenItem.Hidden
        fullScreen -> FullScreenItem.Exit
        else -> FullScreenItem.Enter
    }

    /** Applies [edit] through the session; a refusal with a reason the user can act on becomes a message. True when applied. */
    private fun edit(edit: ProjectEdit): Boolean {
        val rejection = session.edit(edit) ?: return true
        val message = TimelineMessage.EditRejected(rejection)
        if (message.text.isNotEmpty()) local.trySend(message)
        return false
    }

    private fun updateViewport(transform: (TimelineViewport) -> TimelineViewport) {
        view.update { current ->
            val state = session.state.value.project
            val port = current.viewport.copy(
                projectLengthBeats = state?.lengthBeats ?: Beats.Zero,
                beatsPerBar = state?.timeSignature?.beats ?: current.viewport.beatsPerBar,
            )
            current.copy(viewport = transform(port))
        }
    }

    private fun initialZoom(measured: TimelineViewport): TimelineViewport {
        val stored = session.state.value.viewState.zoom
        val fitted = measured.fit()
        return if (stored == null) fitted else fitted.withZoom(stored)
    }

    private fun updateMetronome(transform: (MetronomeSettings) -> MetronomeSettings) {
        viewModelScope.launch(CoroutineName("updateMetronome")) {
            preferences.update { snapshot -> snapshot.copy(settings = transform(snapshot.settings)) }
        }
    }

    private suspend fun observePreferences() = coroutineScope {
        launch(CoroutineName("observeOverviewStrip")) { timelinePreferences.overviewStripShown.collect { overviewStripShown.value = it } }
        preferences.snapshot.collect { snapshot -> metronomeSettings.value = snapshot.settings }
    }

    private fun scheduleViewStateSave() {
        saveViewStateJob?.cancel()
        saveViewStateJob = viewModelScope.launch(CoroutineName("saveViewState")) {
            delay(VIEW_STATE_SAVE_DELAY_MILLIS)
            val current = view.value
            session.saveViewState(ProjectViewState(zoom = current.viewport.zoom, grid = current.grid ?: session.state.value.viewState.grid))
        }
    }

    /** Where imported audio lands: the playhead, snapped to the grid. */
    private fun insertionPoint(): Beats = snapped(currentUi().positionBeats)

    /**
     * [beats] on the nearest line of the current grid (the beats themselves when it is off), never before zero. Reads
     * the grid and the signature directly rather than through [currentUi]: a scrub calls it once per pointer move.
     */
    private fun snapped(beats: Beats): Beats {
        val current = session.state.value
        val grid = view.value.grid ?: current.viewState.grid
        val signature = current.project?.timeSignature ?: TimeSignature.Default
        return Snap.snap(beats, grid, signature)
    }

    private fun pickAudio(target: ImportTarget) {
        viewModelScope.launch(CoroutineName("pickAudio")) {
            val picked = when (target) {
                is ImportTarget.Track -> picker.pickOne(AudioPickRequest.timelineAudio)
                is ImportTarget.NewTracks -> picker.pickMany(AudioPickRequest.timelineAudio)
            }
            when (val result = picked) {
                is AudioPickResult.Picked -> session.importAudio(result.files, target)
                AudioPickResult.Cancelled -> Unit
                is AudioPickResult.Failed -> local.trySend(TimelineMessage.PickFailed)
            }
        }
    }

    /** A newly opened project resets the view: fresh selection, no sheet, the stored or fitted zoom. */
    private suspend fun observeProject() {
        session.state.map { it.project?.id }.distinctUntilChanged().collect { id ->
            view.update { current ->
                if (current.projectId == id) return@update current
                val port = current.viewport
                val fresh = TimelineViewState(projectId = id, viewport = port.copy(scrollBeats = Beats.Zero))
                if (port.widthPx > 0.0) {
                    val project = session.state.value.project
                    val measured = port.copy(scrollBeats = Beats.Zero, projectLengthBeats = project?.lengthBeats ?: Beats.Zero, beatsPerBar = project?.timeSignature?.beats ?: port.beatsPerBar)
                    fresh.copy(viewport = initialZoom(measured))
                } else {
                    fresh
                }
            }
        }
    }

    /** The 10 Hz mirror of the audible position while playing, and the paging that follows the playhead (spec FR-012). */
    private suspend fun pollPosition() {
        while (true) {
            if (session.isPlaying) {
                session.readTransport(snapshot)
                val beats = Beats(snapshot.positionBeats).coerceAtLeastZero()
                val seconds = snapshot.audiblePositionSeconds
                view.update { current ->
                    current.copy(
                        positionBeats = beats,
                        positionSeconds = seconds,
                        viewport = if (current.following) current.viewport.follow(beats) else current.viewport,
                    )
                }
            }
            delay(POLL_MILLIS)
        }
    }

    /** Pages so [beats] is visible: the playhead lands at the left edge when it leaves the view. */
    private fun TimelineViewport.follow(beats: Beats): TimelineViewport {
        if (widthPx <= 0.0) return this
        val inView = beats.value >= scrollBeats.value && beats.value < endBeats.value
        return if (inView) this else scrollTo(beats)
    }

    private companion object {
        const val POLL_MILLIS = 100L
        const val VIEW_STATE_SAVE_DELAY_MILLIS = 500L
        /** For a clip whose asset entry is gone (never in a valid document); the lane still needs a rate to draw. */
        const val DEFAULT_SAMPLE_RATE = 48_000
        val STEP_FACTOR = 2.0.pow(0.5)
    }
}
