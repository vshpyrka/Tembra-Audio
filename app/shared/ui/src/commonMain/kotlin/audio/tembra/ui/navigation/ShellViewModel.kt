package audio.tembra.ui.navigation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the navigation state above the NavDisplay so it outlives destination switches, rotation and live resizes
 * (Constitution II). Intents are plain functions; a relaunch starts on the start destination (spec FR-001, FR-021).
 */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ShellViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    /** Shows [destination]; selecting the active destination changes nothing (spec FR-008). */
    fun select(destination: Destination) {
        if (destination == _uiState.value.selected) return
        _uiState.value = ShellUiState(selected = destination)
    }

    /** Returns to the start destination and reports true, or reports false there so the platform handles back (spec FR-010). */
    fun back(): Boolean {
        if (_uiState.value.selected == StartDestination) return false
        _uiState.value = ShellUiState()
        return true
    }
}
