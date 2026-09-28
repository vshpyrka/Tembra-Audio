package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.tuner.LocalTunerTestSignal
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Stateful wrapper: obtains the ViewModel from the Metro graph and the layout mode from the window. */
@Composable
fun NavigationShell(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = metroViewModel(),
    layoutMode: LayoutMode = rememberLayoutMode(),
) {
    // A debug test signal opens the app on the Tuner so the meter can be captured without a tap (spec FR-032).
    val testSignal = LocalTunerTestSignal.current
    LaunchedEffect(Unit) {
        if (testSignal != null) viewModel.select(Destination.Tuner)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavigationShellContent(
        state = state,
        layoutMode = layoutMode,
        onSelect = viewModel::select,
        onBack = { viewModel.back() },
        modifier = modifier,
    )
}
