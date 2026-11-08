package audio.tembra.ui.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.Percent
import audio.tembra.core.project.SetGain
import audio.tembra.core.project.SetMasterVolume
import audio.tembra.core.project.SetMuted
import audio.tembra.core.project.SetPan
import audio.tembra.core.project.SetSoloed
import audio.tembra.core.project.findTrack
import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.session.MixTelemetry
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.session.SessionState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The mixer window's ViewModel (feature 012, spec FR-021a, research R10): a window is a destination of its own over the
 * same [ProjectSession] the timeline edits, so its strips follow the session's project and every edit it makes is the
 * timeline's edit (one undo history, one coalesced run per gesture until [gestureSettled], spec FR-005). It holds one
 * of the session's metering counts while it lives (spec FR-013). The state ends with the project: [MixerWindowUiState.open]
 * false closes the window.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class MixerWindowViewModel(private val session: ProjectSession) : ViewModel() {

    val uiState: StateFlow<MixerWindowUiState> = session.state.map(::mapState).stateIn(viewModelScope, SharingStarted.Eagerly, mapState(session.state.value))

    val mixTelemetry: MixTelemetry get() = session.mixTelemetry

    val inputTelemetry: InputTelemetry get() = session.inputTelemetry

    init {
        session.setMetering(true)
    }

    fun setGain(id: TrackId, gainDb: GainDb) {
        session.edit(SetGain(id, gainDb))
    }

    fun setPan(id: TrackId, pan: Pan) {
        session.edit(SetPan(id, pan))
    }

    fun toggleMute(id: TrackId) {
        val track = session.state.value.project?.findTrack(id) ?: return
        session.edit(SetMuted(id, !track.muted))
    }

    fun toggleSolo(id: TrackId) {
        val track = session.state.value.project?.findTrack(id) ?: return
        session.edit(SetSoloed(id, !track.soloed))
    }

    fun setMasterVolume(percent: Int) {
        session.edit(SetMasterVolume(Percent.clamped(percent)))
    }

    /** A fader, knob or master drag released: the coalesced run closes, the next change starts a new undo step (spec FR-005). */
    fun gestureSettled() = session.closeEditGroup()

    fun resetTrackClip(id: TrackId) = session.resetTrackClip(id)

    fun resetMasterClip() = session.resetMasterClip()

    override fun onCleared() {
        session.setMetering(false)
    }

    private fun mapState(state: SessionState): MixerWindowUiState {
        val project = state.project ?: return MixerWindowUiState()
        return MixerWindowUiState(
            open = true,
            strips = StripUi.forProject(project, state.recording.armedTrack),
            master = MasterUi(project.masterVolume.value),
            canReorder = !state.recording.takeRunning,
        )
    }
}
