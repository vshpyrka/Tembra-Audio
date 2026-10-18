package audio.tembra.ui.tracksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.project.Beats
import audio.tembra.core.project.DeleteTrack
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Percent
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.ProjectNames
import audio.tembra.core.project.RenameTrack
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetLabel
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetMuted
import audio.tembra.core.project.SetSoloed
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.findTrack
import audio.tembra.core.project.lengthSeconds
import audio.tembra.core.project.tempoMap
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.session.SessionState
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.transport.TransportUiState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The track-settings form's state holder (spec FR-041, FR-042; contracts/timeline-screen.md), shared by the nested
 * screen and the expanded layout's inline row, each in its own ViewModel store: [open] names the track, every field
 * change is one session edit, the name commits on focus loss (an empty name falls back to "Track N"), and the draft
 * travels through `ProjectSession.settingsDraft` when the window crosses 840 dp. The nested screen's transport bar
 * mirrors the session like the Timeline's. One `pollPosition` launch while visible.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class TrackSettingsViewModel(private val session: ProjectSession) : ViewModel() {

    private val view = MutableStateFlow(TrackSettingsViewState())

    val uiState: StateFlow<TrackSettingsUiState> = combine(session.state, view, ::mapState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, mapState(session.state.value, view.value))

    val telemetry: TransportTelemetry get() = session.transportTelemetry

    private val snapshot = TransportSnapshot()
    private var pollJob: Job? = null

    /** Shows [trackId]'s fields; a draft left by the other form of the same track is taken over (spec FR-042). */
    fun open(trackId: TrackId) {
        val carried = session.settingsDraft.value?.takeIf { it.trackId == trackId }
        view.update { TrackSettingsViewState(trackId = trackId, nameDraft = carried?.name) }
    }

    fun screenVisible(visible: Boolean) {
        if (visible) {
            if (pollJob == null) pollJob = viewModelScope.launch(CoroutineName("pollPosition")) { pollPosition() }
        } else {
            pollJob?.cancel()
            pollJob = null
            commitName()
            session.closeEditGroup()
        }
    }

    // --- Fields ------------------------------------------------------------------------------------------------------

    fun editName(text: String) {
        val id = view.value.trackId ?: return
        val draft = text.take(ProjectNames.MAX_TRACK_NAME)
        view.update { it.copy(nameDraft = draft) }
        val track = session.state.value.project?.findTrack(id) ?: return
        session.settingsDraft.value = TrackSettingsDraft(id, draft, track.label, track.gainDb)
    }

    /** Applies the typed name; blank falls back to the next "Track N" (spec FR-020). */
    fun commitName() {
        val state = view.value
        val id = state.trackId ?: return
        val draft = state.nameDraft ?: return
        view.update { it.copy(nameDraft = null) }
        session.settingsDraft.value = null
        val project = session.state.value.project ?: return
        val others = project.tracks.filter { it.id != id }.map { it.name }
        val name = ProjectNames.trackNameOrDefault(draft, others)
        if (name != project.findTrack(id)?.name) session.edit(RenameTrack(id, name))
    }

    fun setLabel(label: InstrumentLabel?) = track { session.edit(SetLabel(it, label)) }

    fun setGain(gainDb: GainDb) = track { session.edit(SetGain(it, gainDb)) }

    fun setMuted(muted: Boolean) = track { session.edit(SetMuted(it, muted)) }

    fun setSoloed(soloed: Boolean) = track { session.edit(SetSoloed(it, soloed)) }

    fun requestDelete() = view.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = view.update { it.copy(confirmDelete = false) }

    /** Deletes the track; the state then reads [TrackSettingsUiState.closed] and the nested screen pops (spec FR-021). */
    fun confirmDelete() {
        view.update { it.copy(confirmDelete = false, nameDraft = null) }
        session.settingsDraft.value = null
        track { session.edit(DeleteTrack(it)) }
    }

    // --- Transport (the nested screen's bar) -----------------------------------------------------------------------

    fun playPause() = if (session.isPlaying) session.pause() else session.play()

    fun skipToStart() = session.skipToStart()

    fun stepBar(delta: Int) = session.stepBar(delta)

    fun setMasterVolume(percent: Int) {
        session.edit(SetMasterVolume(Percent.clamped(percent)))
    }

    fun toggleMetronome() = session.setMetronome(!session.state.value.metronomeOn)

    // --- Mapping -----------------------------------------------------------------------------------------------------

    private fun mapState(state: SessionState, view: TrackSettingsViewState): TrackSettingsUiState {
        val project = state.project
        val track = view.trackId?.let { project?.findTrack(it) }
        val playing = state.isPlaying
        val positionSeconds = when (val playback = state.playback) {
            is PlaybackState.Stopped -> project?.tempoMap?.beatsToSeconds(playback.positionBeats) ?: 0.0
            is PlaybackState.Starting -> project?.tempoMap?.beatsToSeconds(playback.positionBeats) ?: 0.0
            PlaybackState.Playing, PlaybackState.Pausing -> view.positionSeconds
        }
        val transport = TransportUiState(
            playback = state.playback,
            positionSeconds = if (playing) view.positionSeconds else positionSeconds,
            lengthSeconds = project?.lengthSeconds ?: 0.0,
            masterVolume = project?.masterVolume?.value ?: TransportUiState().masterVolume,
            metronomeOn = state.metronomeOn,
            hasProject = project != null,
        )
        if (track == null) return TrackSettingsUiState(trackId = view.trackId, closed = view.trackId != null && project != null, transport = transport)
        return TrackSettingsUiState(
            trackId = track.id,
            name = track.name,
            label = track.label,
            colorIndex = track.colorIndex,
            gainDb = track.gainDb,
            muted = track.muted,
            soloed = track.soloed,
            nameDraft = view.nameDraft,
            confirmDelete = view.confirmDelete,
            closed = false,
            transport = transport,
        )
    }

    private inline fun track(action: (TrackId) -> Unit) {
        val id = view.value.trackId ?: return
        action(id)
    }

    private suspend fun pollPosition() {
        while (true) {
            if (session.isPlaying) {
                session.readTransport(snapshot)
                view.update { it.copy(positionSeconds = snapshot.audiblePositionSeconds, positionBeats = Beats(snapshot.positionBeats)) }
            }
            delay(POLL_MILLIS)
        }
    }

    private companion object {
        const val POLL_MILLIS = 100L
    }
}
