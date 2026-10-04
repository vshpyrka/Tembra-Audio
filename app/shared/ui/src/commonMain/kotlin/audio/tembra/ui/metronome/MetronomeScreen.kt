package audio.tembra.ui.metronome

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.posture.currentTabletopFold
import audio.tembra.ui.posture.currentVerticalFold
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Metronome destination (contracts/metronome-screen.md, research R12): the wiring seam between the entry-scoped
 * [MetronomeViewModel] and the stateless [MetronomeScreenContent]. Visibility comes from this composition (Navigation 3
 * disposes the entry on a destination switch); no lifecycle effect stops the click, which keeps playing with the
 * screen locked or the app in the background by design (spec FR-027). Every effect is keyed on the ViewModel it talks
 * to, so a different instance would be wired afresh; the snackbar collects the one-shot messages.
 */
@Composable
fun MetronomeScreen(modifier: Modifier = Modifier, viewModel: MetronomeViewModel = metroViewModel()) {
    // The debug Syncopated reading (research R14) is static for the process, so the ViewModel key alone restarts this.
    val debug = LocalMetronomeDebug.current
    DisposableEffect(viewModel) {
        viewModel.useDebugSettings(debug)
        viewModel.screenVisible(true)
        onDispose { viewModel.screenVisible(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(MetronomeCopy.of(it)) }
    }
    // A tabletop foldable splits the screen at its fold (feature 005); both are read from the window on every change.
    val fold = currentTabletopFold()
    val layout = MetronomeLayout.from(currentWindowAdaptiveInfo().windowSizeClass, fold, isIphoneDuo)
    val callbacks = remember(viewModel) {
        MetronomeCallbacks.None.copy(
            onTempo = viewModel::setTempo,
            onStep = viewModel::stepTempo,
            onSignature = viewModel::selectSignature,
            onSubdivision = viewModel::selectSubdivision,
            onCycleBeat = viewModel::cycleBeat,
            onStyle = viewModel::selectStyle,
            onSound = viewModel::selectSound,
            onImportCustom = viewModel::importCustomSound,
            onReplaceCustom = viewModel::replaceCustomSound,
            onTap = viewModel::tap,
            onTogglePlayback = viewModel::togglePlayback,
        )
    }
    if (state.loaded) {
        MetronomeScreenContent(
            state = state,
            callbacks = callbacks,
            telemetry = viewModel.telemetry,
            layout = layout,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
            fold = fold,
            verticalFold = currentVerticalFold(),
        )
    }
}
