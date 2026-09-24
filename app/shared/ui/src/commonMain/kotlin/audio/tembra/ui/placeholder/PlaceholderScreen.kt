package audio.tembra.ui.placeholder

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.theme.WindowWidth
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Stateful wrapper: obtains the ViewModel from the Metro graph and measures the window width. */
@Composable
fun PlaceholderScreen(viewModel: PlaceholderViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BoxWithConstraints {
        PlaceholderScreenContent(
            state = state,
            onButtonPressed = viewModel::onButtonPressed,
            windowWidth = WindowWidth.fromWidth(maxWidth),
        )
    }
}
