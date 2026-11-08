package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay

/**
 * The mixer window's root for a platform host (feature 012, spec FR-021a, research R10): the graph's ViewModel factory
 * and the theme around [MixerWindowScreen], the way `App` roots the main window, with the perf anchor root so the
 * journeys and the adb drivers reach the window's controls by tag. Android's `MixerWindowActivity` sets this as its
 * content and finishes on [onProjectGone].
 */
@Composable
fun MixerWindow(appGraph: AppGraph, modifier: Modifier = Modifier, onProjectGone: () -> Unit = {}) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        TembraTheme {
            Box(Modifier.anchorRoot()) { MixerWindowScreen(onProjectGone = onProjectGone, modifier = modifier) }
        }
    }
}

/**
 * The window's screen: the ViewModel's state and its edits as the strips' callbacks; the rest of the timeline's
 * callbacks are absent here. [onProjectGone] fires once the session has had no project for [PROJECT_GONE_MILLIS]: a
 * project switch passes through an empty state for a moment, which the delay lets by.
 */
@Composable
internal fun MixerWindowScreen(onProjectGone: () -> Unit, modifier: Modifier = Modifier, viewModel: MixerWindowViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnProjectGone by rememberUpdatedState(onProjectGone)
    LaunchedEffect(state.open) {
        if (!state.open) {
            delay(PROJECT_GONE_MILLIS)
            currentOnProjectGone()
        }
    }
    val callbacks = remember(viewModel) {
        TimelineCallbacks.None.copy(
            onToggleMute = viewModel::toggleMute,
            onToggleSolo = viewModel::toggleSolo,
            onGain = viewModel::setGain,
            onPan = viewModel::setPan,
            onMasterVolume = viewModel::setMasterVolume,
            onGainSettled = viewModel::gestureSettled,
            onPanSettled = viewModel::gestureSettled,
            onMasterSettled = viewModel::gestureSettled,
            onResetTrackClip = viewModel::resetTrackClip,
            onResetMasterClip = viewModel::resetMasterClip,
        )
    }
    MixerWindowContent(state = state, callbacks = callbacks, telemetry = viewModel.mixTelemetry, inputTelemetry = viewModel.inputTelemetry, modifier = modifier)
}

/** How long the session may be without a project before the window takes it as closed (a switch reopens within a frame or two). */
private const val PROJECT_GONE_MILLIS = 300L
