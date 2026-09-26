package audio.tembra.ui.tuner

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.theme.WindowWidth
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Tuner destination (spec FR-001): binds the entry-scoped [TunerViewModel] to the lifecycle. Visibility comes
 * from this composition (Navigation 3 disposes the entry on a destination switch), foreground from `ON_START` /
 * `ON_STOP`, and the permission is refreshed on every resume (research R7, analysis M1).
 */
@Composable
fun TunerScreen(viewModel: TunerViewModel = metroViewModel(), modifier: Modifier = Modifier) {
    // The test signal is handed over before the screen reports itself visible, so a debug launch never opens the microphone.
    val testSignal = LocalTunerTestSignal.current
    DisposableEffect(Unit) {
        viewModel.useTestSignal(testSignal)
        viewModel.screenVisible(true)
        onDispose { viewModel.screenVisible(false) }
    }
    LifecycleStartEffect(Unit) {
        viewModel.foreground(true)
        onStopOrDispose { viewModel.foreground(false) }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    LaunchedEffect(testSignal) { viewModel.useTestSignal(testSignal) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val windowWidth = WindowWidth.from(currentWindowAdaptiveInfo().windowSizeClass)
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
        TunerScreenContent(state = state, callbacks = callbacks, telemetry = viewModel.telemetry, windowWidth = windowWidth, modifier = modifier)
    }
}
