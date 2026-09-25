package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Stateful wrapper: obtains the ViewModel from the Metro graph and the layout mode from the window. */
@Composable
fun NavigationShell(
    viewModel: ShellViewModel = metroViewModel(),
    layoutMode: LayoutMode = rememberLayoutMode(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavigationShellContent(
        state = state,
        layoutMode = layoutMode,
        onSelect = viewModel::select,
        onBack = { viewModel.back() },
        modifier = modifier,
    )
}
