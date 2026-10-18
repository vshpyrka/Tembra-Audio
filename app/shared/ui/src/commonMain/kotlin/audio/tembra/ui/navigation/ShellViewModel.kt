package audio.tembra.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.DialogRequest
import audio.tembra.ui.session.PlaybackGuard
import audio.tembra.ui.tracksettings.TrackSettingsKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the navigation state above the NavDisplay so it outlives destination switches, rotation and live resizes
 * (Constitution II). Intents are plain functions; a relaunch starts on the start destination (spec FR-001, FR-021 of
 * feature 002).
 *
 * Leaving the Timeline while a project plays asks the [guard] first (spec FR-008a of feature 009): [select] and
 * [back] navigate at once when nothing plays or the target stays on the Timeline (its track settings included);
 * otherwise one named `viewModelScope` launch ([leaveGuarded]) awaits the stop-and-leave answer and navigates only
 * on "Stop and leave". A second request while one is pending is ignored.
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ShellViewModel(private val guard: PlaybackGuard) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    /** The pending stop-and-leave question the shell renders (null when none). */
    val dialog: StateFlow<DialogRequest?> = guard.dialog

    private var pendingLeave: Job? = null

    /** Shows [destination]; selecting the active destination changes nothing (spec FR-008). */
    fun select(destination: Destination) {
        if (destination == _uiState.value.selected) return
        leaveGuarded(to = destination) { _uiState.value = ShellUiState(selected = destination) }
    }

    /**
     * Pops the top entry: the track settings back to the Timeline, or any destination back to the start destination.
     * Reports true when consumed, false on the start destination so the platform handles back (spec FR-010). Leaving
     * the Timeline is guarded; a "Keep playing" answer leaves the stack as it is, with back still consumed.
     */
    fun back(): Boolean {
        val state = _uiState.value
        if (state.trackSettings != null && state.selected == Destination.Timeline) {
            _uiState.value = state.copy(trackSettings = null)
            return true
        }
        if (state.selected == StartDestination) return false
        leaveGuarded(to = StartDestination) { _uiState.value = ShellUiState() }
        return true
    }

    /** Opens [trackId]'s settings above the Timeline (compact windows); ignored elsewhere. */
    fun openTrackSettings(trackId: TrackId) {
        val state = _uiState.value
        if (state.selected != Destination.Timeline) return
        _uiState.value = state.copy(trackSettings = TrackSettingsKey(trackId))
    }

    fun closeTrackSettings() {
        val state = _uiState.value
        if (state.trackSettings == null) return
        _uiState.value = state.copy(trackSettings = null)
    }

    private fun leaveGuarded(to: Destination, navigate: () -> Unit) {
        val leavingPlayback = guard.isPlaying && to != Destination.Timeline
        if (!leavingPlayback) {
            navigate()
            return
        }
        if (pendingLeave?.isActive == true) return
        pendingLeave = viewModelScope.launch {
            if (guard.confirmLeave()) navigate()
        }
    }
}
