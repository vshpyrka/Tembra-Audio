package audio.tembra.ui.tracksettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.core.project.TrackId
import audio.tembra.ui.transport.TransportCallbacks
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The nested track-settings entry's wiring (contracts/timeline-screen.md): the ViewModel from the Metro graph (one per
 * entry store), opened on [trackId], visible while composed; a track that no longer exists pops the entry.
 */
@Composable
fun TrackSettingsScreen(
    trackId: TrackId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackSettingsViewModel = metroViewModel(),
) {
    DisposableEffect(viewModel, trackId) {
        viewModel.open(trackId)
        viewModel.screenVisible(true)
        onDispose { viewModel.screenVisible(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }
    val callbacks = remember(viewModel) { trackSettingsCallbacks(viewModel) }
    val transportCallbacks = remember(viewModel) {
        TransportCallbacks(
            onPlayPause = viewModel::playPause,
            onSkipToStart = viewModel::skipToStart,
            onStepBar = viewModel::stepBar,
            onMasterVolume = viewModel::setMasterVolume,
            onMetronomeToggle = viewModel::toggleMetronome,
            onMetronomeSettings = {},
        )
    }
    TrackSettingsScreenContent(
        state = state,
        callbacks = callbacks,
        transportCallbacks = transportCallbacks,
        telemetry = viewModel.telemetry,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The form's intents bound to [viewModel]; shared with the inline row. */
internal fun trackSettingsCallbacks(viewModel: TrackSettingsViewModel): TrackSettingsCallbacks = TrackSettingsCallbacks(
    onName = viewModel::editName,
    onNameCommit = viewModel::commitName,
    onLabel = viewModel::setLabel,
    onGain = viewModel::setGain,
    onMute = viewModel::setMuted,
    onSolo = viewModel::setSoloed,
    onDelete = viewModel::requestDelete,
    onConfirmDelete = viewModel::confirmDelete,
    onDismissDelete = viewModel::dismissDelete,
)
