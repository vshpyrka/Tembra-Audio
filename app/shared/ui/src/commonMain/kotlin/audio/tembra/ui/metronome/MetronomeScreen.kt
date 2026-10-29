package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.designsystem.PreparedLayout
import audio.tembra.ui.perf.ReportFirstContentDrawn
import audio.tembra.ui.platform.isIphoneDuo
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
    // Fully drawn once the stored settings are in and the first content composes (feature 007, FR-017).
    ReportFirstContentDrawn(state.isFirstContentReady)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(MetronomeCopy.of(it)) }
    }
    // A tabletop foldable splits the screen at its fold (feature 005); both are read from the window on every change.
    val fold = currentTabletopFold()
    val layout = MetronomeLayout.from(currentWindowAdaptiveInfoV2().windowSizeClass, fold, isIphoneDuo)
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
        // The screen's own height, not the window's: under 506 dp of content the dial-first Pocket form takes the place
        // of a Stacked one whose dial would end under the fold (a short desktop window, 2026-10-16). Prepared over a few
        // frames before it shows (PreparedLayout), so opening the screen drops no frame.
        PreparedLayout(modifier = modifier) { constraints ->
            val maxHeight = with(LocalDensity.current) { constraints.maxHeight.toDp() }
            MetronomeScreenContent(
                state = state,
                callbacks = callbacks,
                telemetry = viewModel.telemetry,
                layout = layout.forContentHeight(maxHeight),
                snackbarHostState = snackbarHostState,
                modifier = Modifier.fillMaxSize(),
                fold = fold,
                verticalFold = currentVerticalFold(),
            )
        }
    }
}
