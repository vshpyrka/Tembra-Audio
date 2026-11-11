package audio.tembra.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import audio.tembra.core.project.CountIn
import audio.tembra.core.project.DeleteClip
import audio.tembra.core.project.DeleteTrack
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.MoveClip
import audio.tembra.core.project.MoveDirection
import audio.tembra.core.project.MoveTrack
import audio.tembra.core.project.Pan
import audio.tembra.core.project.Percent
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectEdit
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetMuted
import audio.tembra.core.project.SetPan
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
import audio.tembra.core.recording.RecordingState
import audio.tembra.core.timeline.Snap
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.ProjectViewState
import audio.tembra.data.timeline.TimelinePreferences
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.drop.DropOutcome
import audio.tembra.ui.drop.DropPlan
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.mixer.MasterUi
import audio.tembra.ui.mixer.MixerAnchors
import audio.tembra.ui.mixer.MixerHeight
import audio.tembra.ui.mixer.MixerUi
import audio.tembra.ui.mixer.StripForm
import audio.tembra.ui.mixer.StripUi
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.AudioPickRequest
import audio.tembra.ui.platform.AudioPickResult
import audio.tembra.ui.platform.WindowCapabilities
import audio.tembra.ui.platform.WindowControls
import audio.tembra.ui.session.ImportTarget
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.session.MixTelemetry
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.session.SessionMessage
import audio.tembra.ui.session.SessionState
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.transport.RecordButtonState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.math.pow
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
import kotlin.math.max

/**
 * The Timeline's state holder (contracts/timeline-screen.md; Constitution II): maps the session's project and the
 * view state it owns ([TimelineViewState]) to one [TimelineUiState], and turns every intent into a session call or a
 * view-state update. The playhead per frame is read in draw lambdas through [telemetry]; the 10 Hz mirror for
 * semantics comes from [pollPosition]. Named `viewModelScope` launches: [observeProject], [pollPosition],
 * `pickAudio`, `saveViewState`; all but the pickers are tied to the screen being visible ([screenVisible]).
 *
 * Recording (feature 010): the arm toggle, Record and the clip reset go to the session's controller; the recording
 * phase, the armed track, the growing take and the bar's width ([barWidthMeasured]) are mapped into the state, and the
 * intents spec FR-009 and FR-018 make unavailable while a count-in or a take runs are refused here as well as disabled.
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
    private val windowCapabilities: WindowCapabilities,
) : ViewModel() {

    private val view = MutableStateFlow(TimelineViewState())

    /** Whether this device shows the overview strip (spec FR-015; T151): outside [view], so opening another project keeps it. */
    private val overviewStripShown = MutableStateFlow(true)

    /** The last global metronome settings the preferences emitted: outside [view] for the same reason (T147). */
    private val metronomeSettings = MutableStateFlow(MetronomeSettings())

    /** The transport bar's width (feature 010, spec FR-007): decides where Record and the monitor toggle sit. */
    private val barWidth = MutableStateFlow(0.dp)

    /** The menu's full-screen row from the platform's window controls (feature 011, spec FR-010a). */
    private val fullScreenItem = combine(windowControls.fullScreenAvailable, windowControls.isFullScreen, ::fullScreenItemOf)

    /** The lanes box as the screen measured it (feature 012, spec FR-016, FR-021): the mixer's anchors and strip form follow it. */
    private val lanes = MutableStateFlow(LanesBox())

    /** The window's chrome: the bar's width, the full-screen row and the lanes box, grouped so the state combines five flows. */
    private val chrome = combine(barWidth, fullScreenItem, lanes, ::Triple)

    val uiState: StateFlow<TimelineUiState> = combine(session.state, view, overviewStripShown, metronomeSettings, chrome) { state, view, strip, metronome, (width, fullScreen, lanes) ->
        mapState(state, view, strip, metronome, width, fullScreen, lanes)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentUi())

    /** One-shot snackbar messages: the session's, mapped, merged with the ViewModel's own, to the one collector. */
    private val local = Channel<TimelineMessage>(Channel.BUFFERED)
    val messages: Flow<TimelineMessage> = merge(session.messages.map(::toMessage), local.receiveAsFlow())

    /** Draw-phase handle: the session reads the engine at most once per frame. */
    val telemetry: TransportTelemetry get() = session.transportTelemetry

    /** Draw-phase handle on the input (feature 010): the meter and the growing take read it once per frame. */
    val inputTelemetry: InputTelemetry get() = session.inputTelemetry

    /** Draw-phase handle on the mixer's meters (feature 012): the sheet reads it once per frame. */
    val mixTelemetry: MixTelemetry get() = session.mixTelemetry

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
        if (takeRunning()) return
        view.update { it.copy(following = true, positionBeats = Beats.Zero, positionSeconds = 0.0, viewport = it.viewport.scrollToStart()) }
        session.skipToStart()
    }

    fun stepBar(delta: Int) {
        if (takeRunning()) return
        session.stepBar(delta)
    }

    /** A ruler tap: snapped to the grid, and the view follows the playhead again (spec FR-012); ignored during a take (010 FR-009). */
    fun seek(beats: Beats) {
        if (takeRunning()) return
        val target = snapped(beats)
        view.update { it.copy(following = true, positionBeats = target, viewport = it.viewport.follow(target)) }
        session.seek(target)
    }

    /** A ruler drag (clarification 4): playback pauses for the drag and resumes from the release point. */
    fun scrubStart() {
        if (takeRunning()) return
        resumeAfterScrub = session.isPlaying
        if (resumeAfterScrub) session.pause()
    }

    /** The marker under the finger, snapped to the grid when it is on (spec FR-011, D1). */
    fun scrub(beats: Beats) {
        if (takeRunning()) return
        val target = snapped(beats)
        view.update { it.copy(positionBeats = target) }
        session.seek(target)
    }

    /** The release point, snapped like the drag; playback resumes from it if the drag paused it. */
    fun scrubEnd(beats: Beats) {
        if (takeRunning()) return
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
    fun viewportMeasured(widthPx: Double, densityScale: Double, leadInPx: Double = 0.0) {
        if (widthPx <= 0.0) return
        view.update { current ->
            val port = current.viewport
            if (port.widthPx == widthPx && port.densityScale == densityScale && port.leadInPx == leadInPx) return@update current
            val next = if (port.widthPx <= 0.0) {
                initialZoom(port.copy(widthPx = widthPx, densityScale = densityScale, leadInPx = leadInPx))
            } else {
                port.remeasured(widthPx, densityScale, leadInPx)
            }
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
     * the zoom clamped to the widest and closest levels and [startBeats] kept at the left edge (a span from the project's
     * start keeps the lead-in before it); an empty span is ignored.
     */
    fun zoomTo(startBeats: Beats, endBeats: Beats) {
        val span = endBeats.value - startBeats.value
        if (span <= 0.0) return
        view.update { it.copy(following = false) }
        updateViewport { port ->
            port.zoomToSpan(startBeats, endBeats)
        }
        scheduleViewStateSave()
    }

    /**
     * The overview strip dragged (experiment): [spanBeats] fills the lanes, the zoom clamped to the widest and closest
     * levels and [startBeats] at the left edge, held at the lead-in at the earliest; an empty span is ignored.
     */
    fun zoomWindow(startBeats: Beats, spanBeats: Double) {
        if (spanBeats <= 0.0) return
        view.update { it.copy(following = false) }
        updateViewport { port ->
            port.zoomToVisible(startBeats, spanBeats)
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

    /** A new track at the bottom, selected so the next "Add audio…" lands on it (spec FR-019); refused during a take (010 FR-018). */
    fun addTrack() {
        if (takeRunning()) return
        addTrackSelected()
    }

    /** "+ Track": the new track, selected; its id, or null when the session refused the edit. */
    private fun addTrackSelected(): TrackId? {
        val add = AddTrack()
        if (!edit(add)) return null
        view.update { it.copy(selection = Selection.TrackSelected(add.id)) }
        return add.id
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

    /** The strip's knob or the header's readout (feature 012, spec FR-007): one coalesced run per track until [gestureSettled]. */
    fun setPan(id: TrackId, pan: Pan) = edit(SetPan(id, pan))

    fun moveTrack(id: TrackId, delta: Int) {
        if (delta == 0 || takeRunning()) return
        edit(MoveTrack(id, if (delta < 0) MoveDirection.Up else MoveDirection.Down))
    }

    fun requestDeleteTrack(id: TrackId) {
        if (takeRunning()) return
        view.update { it.copy(confirmDelete = id) }
    }

    fun confirmDeleteTrack() {
        val id = view.value.confirmDelete ?: return
        if (takeRunning()) return
        view.update { it.copy(confirmDelete = null, selection = if (it.selection.trackIdOrNull == id) Selection.None else it.selection, expandedTrack = it.expandedTrack.takeUnless { track -> track == id }) }
        edit(DeleteTrack(id))
    }

    fun dismissDeleteTrack() = view.update { it.copy(confirmDelete = null) }

    /** The expanded layout's inline settings row: open for [id], closed for null (spec FR-041). */
    fun expandTrack(id: TrackId?) = view.update { it.copy(expandedTrack = id) }

    /** One file onto [trackId] or the selected track; with no selection the toolbar's entry behaves as "as new track" (spec FR-043). */
    fun addAudio(trackId: TrackId?) {
        if (takeRunning()) return
        val target = trackId ?: currentUi().selection.trackIdOrNull
        pickAudio(if (target == null) ImportTarget.NewTracks(insertionPoint()) else ImportTarget.Track(target, insertionPoint()))
    }

    fun addAudioAsNewTrack() {
        if (takeRunning()) return
        pickAudio(ImportTarget.NewTracks(insertionPoint()))
    }

    // --- Recording (feature 010) --------------------------------------------------------------------------------------

    /** The transport bar's width from the screen's constraints: Record and the monitor toggle move with it (spec FR-007, FR-026). */
    fun barWidthMeasured(width: Dp) {
        barWidth.value = width
    }

    // --- The mixer (feature 012, contracts/mixer-ui.md, mixer-session.md) ------------------------------------------------

    /**
     * The Mixer button and the X key (spec FR-017, FR-018): opens the sheet at its half height and starts the meters (the
     * sheet holds one of the session's metering counts), or closes it and releases them. Never touches the transport
     * (FR-024); the close is the `timeline.mixerClose` section.
     */
    fun toggleMixer() {
        // While the mixer has a window of its own, the button and X bring that window to the front (spec FR-021a).
        if (windowCapabilities.mixerWindowOpen.value) {
            windowCapabilities.openMixerWindow()
            return
        }
        if (view.value.mixer.open) {
            PerfTrace.section(PerfSection.TIMELINE_MIXER_CLOSE) {
                view.update { it.copy(mixer = it.mixer.copy(open = false)) }
                session.setMetering(false)
            }
        } else {
            view.update { it.copy(mixer = it.mixer.copy(open = true, height = MixerHeight.Half)) }
            session.setMetering(true)
        }
    }

    /**
     * The handle's "Open in a window" (spec FR-021a): the sheet closes (its metering count goes with it; the window holds
     * its own) and the platform opens the mixer's window over the same session.
     */
    fun openMixerWindow() {
        if (view.value.mixer.open) toggleMixer()
        windowCapabilities.openMixerWindow()
    }

    /** The height a settled drag or the handle asked for (spec FR-018); landscape's single anchor overrides it when mapped. */
    fun setMixerHeight(height: MixerHeight) = view.update { it.copy(mixer = it.mixer.copy(height = height)) }

    /** A fader, knob or master drag released: the coalesced run closes, the next change starts a new undo step (spec FR-005). */
    fun gestureSettled() = session.closeEditGroup()

    /** A strip's clip square tapped: clears that track's latch at the engine's next block (spec FR-012). */
    fun resetTrackClip(id: TrackId) = session.resetTrackClip(id)

    /** The master's clip square tapped: clears the master latch (spec FR-012). */
    fun resetMasterClip() = session.resetMasterClip()

    /** The lanes box measured under [layout] (spec FR-016, FR-021, FR-022): the anchors and the strip form derive from it. */
    fun lanesMeasured(layout: TimelineLayout, height: Dp) {
        lanes.value = LanesBox(layout, height)
    }

    override fun onCleared() {
        // The sheet dies with the ViewModel: its metering count is released so the engine stops measuring (FR-013).
        if (view.value.mixer.open) session.setMetering(false)
    }

    /** The arm toggle (spec FR-001, FR-005): arms [id], disarms it when it is the armed one; nothing during a take (FR-018). */
    fun arm(id: TrackId) {
        val recording = session.state.value.recording
        if (recording.takeRunning) return
        if (recording.armedTrack == id) session.disarm() else session.arm(id)
    }

    /**
     * Record (spec FR-008, FR-009): ends a running take, cancels a count-in, starts a take on the armed track, or arms
     * first: the selected track, or a track added as "+ Track" does when nothing is selected.
     */
    fun record() {
        when (session.state.value.recording.phase) {
            is RecordingState.Recording -> session.stopRecording()
            is RecordingState.PreRoll -> session.cancelRecording()
            is RecordingState.Finalising -> Unit
            is RecordingState.Arming, is RecordingState.Armed -> session.record()
            RecordingState.Disarmed -> {
                val target = currentUi().selection.trackIdOrNull ?: addTrackSelected() ?: return
                session.record(armFirst = target)
            }
        }
    }

    /** Ends the running take and keeps it (spec FR-009); nothing otherwise. */
    fun stopRecording() = session.stopRecording()

    /** The meter's clip square tapped: the latch clears (spec FR-006). */
    fun resetClip() = session.resetClip()

    fun toggleMonitor() = session.toggleMonitor()

    /** The metronome sheet's count-in (spec FR-015): a project setting, read-only during a take (FR-018). */
    fun setCountIn(countIn: CountIn) {
        if (takeRunning()) return
        session.setCountIn(countIn)
    }

    /** The denied snackbar's action (spec FR-020): the system settings, or the prompt again. */
    fun requestMicrophone() = session.requestMicrophone()

    private fun takeRunning(): Boolean = session.state.value.recording.takeRunning

    /**
     * Files dropped on the timeline (feature 011, spec FR-026): the planned target goes to the session, which names the
     * items the drop left out in its refusal message; with no audio at all, a message says so. Projects' plan never
     * reaches the timeline.
     */
    fun drop(outcome: DropOutcome) {
        when (val plan = outcome.plan) {
            is DropPlan.AddToTrack -> session.importAudio(listOf(plan.file), ImportTarget.Track(plan.trackId, plan.atBeats), outcome.skipped)
            is DropPlan.NewTracks -> session.importAudio(plan.files, ImportTarget.NewTracks(plan.atBeats), outcome.skipped)
            is DropPlan.NewProject -> session.importAudio(plan.files, ImportTarget.NewTracks(insertionPoint()), outcome.skipped)
            DropPlan.Nothing -> local.trySend(TimelineMessage.NoAudioFound(outcome.skipped))
        }
    }

    // --- Clips -------------------------------------------------------------------------------------------------------

    fun selectClip(trackId: TrackId, clipId: ClipId) = view.update { it.copy(selection = Selection.ClipSelected(trackId, clipId)) }

    fun clearSelection() = view.update { it.copy(selection = Selection.None) }

    /** Splits the selected clip at the playhead (spec FR-032); the new right half becomes the selection. */
    fun split() {
        val state = currentUi()
        val clip = state.selectedClip ?: return
        val trackId = state.selection.trackIdOrNull ?: return
        if (!state.canSplit || takeRunning()) return
        val split = SplitClip(clip.id, Snap.snap(state.positionBeats, state.grid, state.timeSignature))
        PerfTrace.section(PerfSection.TIMELINE_CLIP_SPLIT) {
            if (edit(split)) view.update { it.copy(selection = Selection.ClipSelected(trackId, split.rightId)) }
        }
    }

    fun moveClip(clipId: ClipId, toTrack: TrackId, startBeats: Beats) {
        if (takeRunning()) return
        val state = currentUi()
        val target = Snap.snap(startBeats, state.grid, state.timeSignature)
        if (edit(MoveClip(clipId, toTrack, target))) view.update { it.copy(selection = Selection.ClipSelected(toTrack, clipId)) }
    }

    /** A long press (touch) or a press past the slop (pointer) lifts the clip; it is selected and follows the finger (spec FR-033). */
    fun clipDragStart(clipId: ClipId) {
        if (takeRunning()) return
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
        if (takeRunning()) return
        if (edit(DeleteClip(clipId))) view.update { current -> current.copy(selection = current.selection.takeUnless { it is Selection.ClipSelected && it.clipId == clipId } ?: Selection.None) }
    }

    fun undo() {
        if (takeRunning()) return
        session.undo()
    }

    fun redo() {
        if (takeRunning()) return
        session.redo()
    }

    // --- Tempo, signature, sheets ------------------------------------------------------------------------------------

    fun openSheet(sheet: TimelineSheet?) = view.update { it.copy(sheet = sheet) }

    /** The tempo dialog's live drag: the project follows at once so the ruler and the click show it (spec FR-035). */
    fun tempoDragging(tempo: Tempo) {
        if (takeRunning()) return
        edit(SetTempo(tempo))
    }

    fun tempoCommit(tempo: Tempo) {
        if (takeRunning()) return
        edit(SetTempo(tempo))
        session.closeEditGroup()
    }

    fun setTimeSignature(signature: TimeSignature) {
        if (takeRunning()) return
        edit(SetTimeSignature(signature))
    }

    /** A tap in the tempo dialog: the run's average becomes the tempo at once; the run ends two seconds after the last tap, as one undo step. */
    fun tapTempo(atNanos: Long) {
        if (takeRunning()) return
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
        barWidth: Dp,
        fullScreenItem: FullScreenItem,
        lanes: LanesBox,
    ): TimelineUiState {
        val project = state.project ?: return TimelineUiState(
            loaded = state.unavailable != null,
            playback = state.playback,
            overviewStripShown = overviewStripShown,
            fullScreenItem = fullScreenItem,
            metronomeSettings = metronomeSettings,
            barWidth = barWidth,
        )
        val tempoMap = project.tempoMap
        val audible = project.audibleTracks
        val recording = state.recording
        val armedTrack = recording.armedTrack
        val recordInBar = barWidth >= RecordInBarFrom
        val takeRunning = recording.takeRunning
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
                armed = track.id == armedTrack,
                recordHere = track.id == armedTrack && !recordInBar,
                pan = track.pan,
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
        // The readout's length and the strip grow with the take once it passes the project's end (spec FR-010).
        val liveTake = recording.liveTake
        val takeEndBeats = liveTake?.let { it.startBeats + tempoMap.secondsToBeats(it.lengthSeconds) }
        val lengthBeats = if (takeEndBeats != null && takeEndBeats > project.lengthBeats) takeEndBeats else project.lengthBeats
        val lengthSeconds = max(project.lengthSeconds, takeEndBeats?.let(tempoMap::beatsToSeconds) ?: 0.0)
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
        val canSplit = selectedClip != null && !takeRunning && run {
            val start = tempoMap.beatsToSeconds(selectedClip.startBeats)
            val end = tempoMap.beatsToSeconds(selectedClip.startBeats + project.clipLengthBeats(selectedClip))
            val at = tempoMap.beatsToSeconds(Snap.snap(positionBeats, grid, project.timeSignature))
            at - start > ProjectLimits.MIN_SPLIT_EDGE_SECONDS && end - at > ProjectLimits.MIN_SPLIT_EDGE_SECONDS
        }
        val ready = tracks.all { track -> track.clips.all { !it.available || it.overviewStatus !is OverviewStatus.Building } }
        // The mixer (feature 012, data-model.md §4, §5): one strip per track, the heights from the lanes box, landscape's one anchor.
        val anchors = MixerAnchors.of(lanes.layout, lanes.height)
        val mixerHeight = if (anchors.singleAnchor) MixerHeight.Full else view.mixer.height
        val mixer = MixerUi(
            open = view.mixer.open,
            height = mixerHeight,
            form = StripForm.of(mixerHeight, anchors.heightOf(mixerHeight).value),
            anchors = anchors,
            strips = StripUi.forProject(project, armedTrack),
            master = MasterUi(project.masterVolume.value),
            selectedIndex = selection.trackIdOrNull?.let { id -> tracks.indexOfFirst { it.id == id }.takeIf { it >= 0 } },
            canReorder = !takeRunning,
            windowAvailable = windowCapabilities.multipleWindows,
        )
        return TimelineUiState(
            loaded = true,
            projectName = project.name,
            tempo = project.tempo,
            timeSignature = project.timeSignature,
            tracks = tracks,
            lengthBeats = lengthBeats,
            lengthSeconds = lengthSeconds,
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
            canUndo = state.canUndo && !takeRunning,
            canRedo = state.canRedo && !takeRunning,
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
            takeRunning = takeRunning,
            recordPhase = when (recording.phase) {
                RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed -> RecordButtonState.Idle
                is RecordingState.PreRoll -> RecordButtonState.CountIn
                is RecordingState.Recording, is RecordingState.Finalising -> RecordButtonState.Recording
            },
            countInBeatsLeft = (recording.phase as? RecordingState.PreRoll)?.beatsLeft ?: -1,
            monitor = recording.monitor,
            countIn = project.countIn,
            liveTake = liveTake,
            permission = recording.permission,
            barWidth = barWidth,
            mixer = mixer,
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
        is SessionMessage.MicrophoneDenied -> TimelineMessage.MicrophoneDenied(message.openSettings)
        SessionMessage.MonitorNeedsHeadphones -> TimelineMessage.MonitorNeedsHeadphones
        SessionMessage.RecordRefused -> TimelineMessage.RecordRefused
        SessionMessage.NoMicrophone -> TimelineMessage.NoMicrophone
        is SessionMessage.TakeEnded -> TimelineMessage.TakeEnded(message.reason)
    }

    // --- Helpers -----------------------------------------------------------------------------------------------------

    /** The state an intent acts on, mapped now from the sources of truth: [uiState] is derived asynchronously. */
    private fun currentUi(): TimelineUiState = mapState(
        session.state.value,
        view.value,
        overviewStripShown.value,
        metronomeSettings.value,
        barWidth.value,
        fullScreenItemOf(windowControls.fullScreenAvailable.value, windowControls.isFullScreen.value),
        lanes.value,
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
                // The mixer sheet stays as it was (feature 012, spec FR-022): its metering count belongs to the sheet, not the project.
                val fresh = TimelineViewState(projectId = id, viewport = port.copy(scrollBeats = Beats.Zero), mixer = current.mixer)
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

    /** Pages so [beats] is visible: when it leaves the view the playhead lands just past the left edge, by the lead-in. */
    private fun TimelineViewport.follow(beats: Beats): TimelineViewport {
        if (widthPx <= 0.0) return this
        val inView = beats.value >= scrollBeats.value && beats.value < endBeats.value
        return if (inView) this else scrollTo(Beats(beats.value - leadInPx / devicePixelsPerBeat))
    }

    private companion object {
        const val POLL_MILLIS = 100L
        const val VIEW_STATE_SAVE_DELAY_MILLIS = 500L
        /** For a clip whose asset entry is gone (never in a valid document); the lane still needs a rate to draw. */
        const val DEFAULT_SAMPLE_RATE = 48_000
        val STEP_FACTOR = 2.0.pow(0.5)
    }
}
