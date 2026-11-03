package audio.tembra.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.ui.drop.DropPlan
import audio.tembra.ui.drop.DropPlanner
import audio.tembra.ui.drop.DropZone
import audio.tembra.ui.drop.DroppedItem
import audio.tembra.ui.drop.OpenedFiles
import audio.tembra.ui.drop.ProjectFromAudio
import audio.tembra.ui.session.DialogRequest
import audio.tembra.ui.session.PlaybackGuard
import audio.tembra.ui.session.SessionActivity
import audio.tembra.ui.tracksettings.TrackSettingsKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineName
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
class ShellViewModel(
    private val guard: PlaybackGuard,
    activity: SessionActivity,
    openedFiles: OpenedFiles,
    private val projectFromAudio: ProjectFromAudio,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    /** The pending stop-and-leave question the shell renders (null when none). */
    val dialog: StateFlow<DialogRequest?> = guard.dialog

    /** True while the session works for a noticeable time: the window shows the busy pointer (feature 011, FR-015a). */
    val busy: StateFlow<Boolean> = activity.busy

    private var pendingLeave: Job? = null

    init {
        // A new window's shell starts on the start destination: the session learns it shows, so it takes the device back
        // from the standalone metronome or tuner a previous window left it with (found on the desktop emulator, 011).
        guard.transportHostVisible(_uiState.value.selected.hostsTransport)
        // Audio opened with the app (feature 011, spec FR-026a): one request at a time, in order.
        viewModelScope.launch(CoroutineName("openedFiles")) {
            openedFiles.files.collect { items -> openFiles(items) }
        }
    }

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

    /**
     * Makes [items] a new project shown on the Timeline (contracts/drop-import.md behaviour 5): planned as a drop on
     * Projects; while a project plays the stop-and-leave question comes first, and "Keep playing" drops the request.
     * With no audio file among them nothing happens.
     */
    private suspend fun openFiles(items: List<DroppedItem>) {
        val outcome = DropPlanner.plan(DropZone.ProjectsGrid, items, Beats.Zero)
        val plan = outcome.plan as? DropPlan.NewProject ?: return
        if (guard.isPlaying && !guard.confirmLeave()) return
        if (!projectFromAudio.start(plan.files, outcome.skipped)) return
        guard.transportHostVisible(Destination.Timeline.hostsTransport)
        _uiState.value = ShellUiState(selected = Destination.Timeline)
    }

    private fun leaveGuarded(to: Destination, navigate: () -> Unit) {
        // The device changes hands before the destination does: the next screen's audio starts on an idle engine.
        val go = {
            guard.transportHostVisible(to.hostsTransport)
            navigate()
        }
        val leavingPlayback = guard.isPlaying && !to.hostsTransport
        if (!leavingPlayback) {
            go()
            return
        }
        if (pendingLeave?.isActive == true) return
        pendingLeave = viewModelScope.launch {
            if (guard.confirmLeave()) go()
        }
    }
}
