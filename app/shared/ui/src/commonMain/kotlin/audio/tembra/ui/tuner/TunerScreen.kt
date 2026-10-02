package audio.tembra.ui.tuner

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.posture.currentTabletopFold
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
    // microphone. The local is set once at the root for the process, so no separate effect follows it.
    val testSignal = LocalTunerTestSignal.current
    DisposableEffect(viewModel) {
        viewModel.useTestSignal(testSignal)
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
        )
    }
    if (state.loaded) {
        TunerScreenContent(state = state, callbacks = callbacks, telemetry = viewModel.telemetry, layout = layout, modifier = modifier, fold = fold)
    }
}
