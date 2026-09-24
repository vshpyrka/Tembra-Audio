package audio.tembra.ui.placeholder

import androidx.lifecycle.ViewModel
import audio.tembra.engine.AudioEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** State holder of the placeholder screen: MVVM with one immutable state (Constitution II). */
@Inject
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class PlaceholderViewModel(engine: AudioEngine) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceholderUiState(engine = engine.describe()))
    val uiState: StateFlow<PlaceholderUiState> = _uiState.asStateFlow()

    fun onButtonPressed() {
        _uiState.update { it.copy(pressCount = it.pressCount + 1) }
    }
}
