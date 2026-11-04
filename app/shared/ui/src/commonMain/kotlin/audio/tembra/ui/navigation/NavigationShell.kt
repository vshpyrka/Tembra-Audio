package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.metronome.LocalMetronomeDebug
import audio.tembra.ui.tuner.LocalTunerNoiseGate
import audio.tembra.ui.tuner.LocalTunerTestSignal
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Stateful wrapper: obtains the ViewModel from the Metro graph and the layout mode from the window. */
@Composable
fun NavigationShell(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = metroViewModel(),
    layoutMode: LayoutMode = rememberLayoutMode(),
) {
    // A debug test signal opens the app on the Tuner so the meter can be captured without a tap (feature 003, spec
    // FR-032), as does a debug noise gate (feature 006, spec FR-023); a debug metronome request likewise opens it on
    // the Metronome (feature 004, spec FR-037).
    val testSignal = LocalTunerTestSignal.current
    val noiseGate = LocalTunerNoiseGate.current
    val metronomeDebug = LocalMetronomeDebug.current
    LaunchedEffect(Unit) {
        when {
            testSignal != null || noiseGate != null -> viewModel.select(Destination.Tuner)
            metronomeDebug != null -> viewModel.select(Destination.Metronome)
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NavigationShellContent(
        state = state,
        layoutMode = layoutMode,
        onSelect = viewModel::select,
        onBack = { viewModel.back() },
        modifier = modifier,
        dialog = dialog,
        busy = busy,
        onOpenTrackSettings = viewModel::openTrackSettings,
        onCloseTrackSettings = viewModel::closeTrackSettings,
    )
}
