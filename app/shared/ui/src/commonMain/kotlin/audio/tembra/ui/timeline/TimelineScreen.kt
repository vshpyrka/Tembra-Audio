package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.core.project.TrackId
import audio.tembra.ui.navigation.rememberLayoutMode
import audio.tembra.ui.perf.ReportFirstContentDrawn
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Timeline destination's wiring (contracts/timeline-screen.md; Constitution II): the ViewModel from the Metro
 * graph, the layout from the screen's own size and the window's layout mode, the one-shot messages into the
 * snackbar, visibility and the save flush on dispose. Track settings open inline on expanded windows and as the
 * nested entry elsewhere ([onOpenTrackSettings], spec FR-041). First content is reported once every visible
 * overview is drawn (feature 007).
 */
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = metroViewModel(),
    onOpenTrackSettings: (TrackId) -> Unit = {},
    nestedTrackSettings: TrackId? = null,
    onCloseTrackSettings: () -> Unit = {},
) {
    DisposableEffect(viewModel) {
        viewModel.screenVisible(true)
        onDispose {
            viewModel.screenVisible(false)
            viewModel.flushSave()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportFirstContentDrawn(state.isFirstContentReady)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.text) }
    }
    val layoutMode = rememberLayoutMode()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = TimelineLayout.from(maxWidth, maxHeight, layoutMode)
        // The hand-off across 840 dp (spec FR-042): the open form moves between the nested entry and the inline row;
        // the typed draft travels through the session, so nothing is lost either way.
        LaunchedEffect(layout.inlineSettings, nestedTrackSettings, state.expandedTrack) {
            if (layout.inlineSettings && nestedTrackSettings != null) {
                viewModel.expandTrack(nestedTrackSettings)
                onCloseTrackSettings()
            } else if (!layout.inlineSettings && state.expandedTrack != null) {
                val id = state.expandedTrack
                viewModel.expandTrack(null)
                if (id != null) onOpenTrackSettings(id)
            }
        }
        val callbacks = remember(viewModel, layout, onOpenTrackSettings) {
            TimelineCallbacks(
                onPlayPause = viewModel::playPause,
                onSkipToStart = viewModel::skipToStart,
                onStepBar = viewModel::stepBar,
                onSeek = viewModel::seek,
                onScrubStart = viewModel::scrubStart,
                onScrub = viewModel::scrub,
                onScrubEnd = viewModel::scrubEnd,
                onMasterVolume = viewModel::setMasterVolume,
                onMetronomeToggle = viewModel::toggleMetronome,
                onMetronomeSettings = { viewModel.openSheet(TimelineSheet.MetronomeSettings) },
                onViewportMeasured = viewModel::viewportMeasured,
                onZoomAround = viewModel::zoomAround,
                onZoomStep = viewModel::zoomStep,
                onScrollBy = viewModel::scrollBy,
                onScrollTo = viewModel::scrollTo,
                onZoomTo = viewModel::zoomTo,
                onOverviewStripShown = viewModel::setOverviewStripShown,
                onFullScreen = viewModel::setFullScreen,
                onGrid = viewModel::setGrid,
                onAddTrack = viewModel::addTrack,
                onSelectTrack = viewModel::selectTrack,
                onToggleMute = viewModel::toggleMute,
                onToggleSolo = viewModel::toggleSolo,
                onGain = viewModel::setGain,
                onTrackSettings = { id -> if (layout.inlineSettings) viewModel.expandTrack(id) else onOpenTrackSettings(id) },
                onMoveTrack = viewModel::moveTrack,
                onDeleteTrack = viewModel::requestDeleteTrack,
                onConfirmDeleteTrack = viewModel::confirmDeleteTrack,
                onDismissDeleteTrack = viewModel::dismissDeleteTrack,
                onAddAudio = viewModel::addAudio,
                onAddAudioAsNewTrack = viewModel::addAudioAsNewTrack,
                onExpandTrack = viewModel::expandTrack,
                onSelectClip = viewModel::selectClip,
                onClearSelection = viewModel::clearSelection,
                onSplit = viewModel::split,
                onMoveClip = viewModel::moveClip,
                onDeleteClip = viewModel::deleteClip,
                onClipDragStart = viewModel::clipDragStart,
                onClipDrag = viewModel::clipDrag,
                onClipDragEnd = viewModel::clipDragEnd,
                onClipDragCancel = viewModel::clipDragCancel,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onOpenSheet = viewModel::openSheet,
                onTempoDragging = viewModel::tempoDragging,
                onTempoCommit = viewModel::tempoCommit,
                onTapTempo = viewModel::tapTempo,
                onTimeSignature = viewModel::setTimeSignature,
                onClickSound = viewModel::selectClickSound,
                onImportCustomSound = viewModel::importCustomSound,
                onSubdivision = viewModel::selectSubdivision,
                onCycleBeat = viewModel::cycleBeat,
                onStyle = viewModel::selectStyle,
            )
        }
        TimelineScreenContent(
            state = state,
            callbacks = callbacks,
            telemetry = viewModel.telemetry,
            layout = layout,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
