package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.designsystem.PreparedLayout
import audio.tembra.ui.perf.ReportFirstContentDrawn
import audio.tembra.ui.posture.currentTabletopFold
import audio.tembra.ui.posture.currentVerticalFold
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Tuner destination (spec FR-001): binds the entry-scoped [TunerViewModel] to the lifecycle. Visibility comes
 * from this composition (Navigation 3 disposes the entry on a destination switch), foreground from `ON_START` /
 * `ON_STOP`, and the permission is refreshed on every resume (research R7, analysis M1). Every effect is keyed on the
 * ViewModel it talks to, so a different instance would be told about the lifecycle afresh.
 */
@Composable
fun TunerScreen(modifier: Modifier = Modifier, viewModel: TunerViewModel = metroViewModel()) {
    // The test signal is handed over before the screen reports itself visible, so a debug launch never opens the
    // microphone, and the launch value of the noise gate with it, so it is in force from the first sound (feature
    // 006). The locals are set once at the root for the process, so no separate effect follows them.
    val testSignal = LocalTunerTestSignal.current
    val noiseGate = LocalTunerNoiseGate.current
    DisposableEffect(viewModel) {
        viewModel.useTestSignal(testSignal)
        viewModel.useNoiseGate(noiseGate)
        viewModel.screenVisible(true)
        onDispose { viewModel.screenVisible(false) }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.foreground(true)
        onStopOrDispose { viewModel.foreground(false) }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Fully drawn once the preferences are in and the first content composes (feature 007, FR-017).
    ReportFirstContentDrawn(state.isFirstContentReady)
    // A tabletop foldable splits the screen at its fold (feature 005); both are read from the window on every change.
    val fold = currentTabletopFold()
    val layout = TunerLayout.from(currentWindowAdaptiveInfo().windowSizeClass, fold)
    val callbacks = remember(viewModel) {
        TunerCallbacks(
            onInstrument = viewModel::selectInstrument,
            onGuitarStrings = viewModel::selectGuitarStrings,
            onPreset = viewModel::selectPreset,
            onMode = viewModel::selectMode,
            onLoopTone = viewModel::setLoopTone,
            onAudition = viewModel::audition,
            onStopAudition = viewModel::stopAudition,
            onRequestMicrophone = viewModel::requestMicrophone,
            onNoiseGate = viewModel::setNoiseGate,
            onNoiseGateSettled = viewModel::noiseGateSettled,
        )
    }
    if (state.loaded) {
        // The screen's own height, not the window's: under 506 dp of content the one-row Pocket form takes the place of
        // a Stacked one whose second selector row pushed the meter and the noise gate down (a short desktop window).
        // The content is composed for that height over the frames before it shows (PreparedLayout), so opening the
        // screen drops no frame.
        PreparedLayout(modifier = modifier) { constraints ->
            val maxHeight = with(LocalDensity.current) { constraints.maxHeight.toDp() }
            TunerScreenContent(
                state = state,
                callbacks = callbacks,
                telemetry = viewModel.telemetry,
                layout = layout.forContentHeight(maxHeight),
                modifier = Modifier.fillMaxSize(),
                fold = fold,
                verticalFold = currentVerticalFold(),
            )
        }
    }
}
