package audio.tembra.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.TempoMap
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.DeleteTrackDialog
import audio.tembra.ui.timeline.components.EmptyTimeline
import audio.tembra.ui.timeline.components.PlayheadOverlay
import audio.tembra.ui.metronome.components.TimeSignatureMenu
import audio.tembra.ui.timeline.components.MetronomeSettingsSheet
import audio.tembra.ui.timeline.components.TempoDialog
import audio.tembra.ui.timeline.components.TempoPill
import audio.tembra.ui.timeline.components.TimelineRuler
import audio.tembra.ui.timeline.components.TimelineToolbar
import audio.tembra.ui.timeline.components.TrackList
import audio.tembra.ui.transport.TransportBar
import audio.tembra.ui.transport.TransportCallbacks

/**
 * The stateless Timeline (contracts/timeline-screen.md "Layouts"): toolbar, pinned ruler, the track list under the
 * playhead overlay, and the transport bar, arranged per [layout]. On expanded windows the tempo and signature pills
 * take the 200 dp header column so the ruler's canvas starts where the lanes do; elsewhere they float over the
 * ruler's start and the canvas spans the full width, which keeps the playhead aligned between ruler and lanes. The
 * root is tagged `screen.timeline` with `stateDescription` "ready" once every visible overview is drawn
 * (contracts/perf-instrumentation.md), plus the 1 dp `screen.timeline.ready` marker the Android journey waits on.
 */
@Composable
internal fun TimelineScreenContent(
    state: TimelineUiState,
    callbacks: TimelineCallbacks,
    telemetry: TransportTelemetry,
    layout: TimelineLayout,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val daw = TembraTheme.daw
    val tempoMap = TempoMap(state.tempo, state.timeSignature)
    val ready = state.isFirstContentReady
    val transportHeight = if (layout == TimelineLayout.Expanded) spacing.transportHeightExpanded else spacing.transportHeightCompact
    val transportCallbacks = TransportCallbacks(
        onPlayPause = callbacks.onPlayPause,
        onSkipToStart = callbacks.onSkipToStart,
        onStepBar = callbacks.onStepBar,
        onMasterVolume = callbacks.onMasterVolume,
        onMetronomeToggle = callbacks.onMetronomeToggle,
        onMetronomeSettings = callbacks.onMetronomeSettings,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(daw.canvasLevel0)
            .testTag(TimelineTestTags.ROOT)
            .semantics { stateDescription = if (ready) READY else LOADING }
            .timelineKeyHandling({ state }, callbacks),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimelineToolbar(
                state = state,
                layout = layout,
                onAddTrack = callbacks.onAddTrack,
                onAddAudio = { callbacks.onAddAudio(null) },
                onAddAudioAsNewTrack = callbacks.onAddAudioAsNewTrack,
                onSplit = callbacks.onSplit,
                onUndo = callbacks.onUndo,
                onRedo = callbacks.onRedo,
                onGrid = callbacks.onGrid,
                onFitProject = callbacks.onFitProject,
                onZoomStep = callbacks.onZoomStep,
                onMetronomeSettings = callbacks.onMetronomeSettings,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                TimelineRuler(
                    viewport = state.viewport,
                    tempoMap = tempoMap,
                    playing = state.isPlaying,
                    positionBeats = state.positionBeats,
                    telemetry = telemetry,
                    onSeek = callbacks.onSeek,
                    onScrubStart = callbacks.onScrubStart,
                    onScrub = callbacks.onScrub,
                    onScrubEnd = callbacks.onScrubEnd,
                    onZoomAround = callbacks.onZoomAround,
                    onScrollBy = callbacks.onScrollBy,
                    leftPills = {
                        if (layout.pinnedHeaders) {
                            RulerPills(state, callbacks, modifier = Modifier.width(spacing.headerColumnWidth))
                        }
                    },
                )
                if (!layout.pinnedHeaders) {
                    RulerPills(state, callbacks, modifier = Modifier.align(Alignment.CenterStart).padding(start = spacing.xs))
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isEmpty) {
                    EmptyTimeline(onAddTrack = callbacks.onAddTrack, onAddAudio = callbacks.onAddAudioAsNewTrack)
                } else {
                    TrackList(state = state, layout = layout, callbacks = callbacks)
                }
                PlayheadOverlay(
                    viewport = state.viewport,
                    signature = state.timeSignature,
                    playing = state.isPlaying,
                    positionBeats = state.positionBeats,
                    telemetry = telemetry,
                    modifier = Modifier.fillMaxSize().padding(start = if (layout.pinnedHeaders) spacing.headerColumnWidth else 0.dp),
                )
            }
            TransportBar(
                state = state.transport,
                callbacks = transportCallbacks,
                telemetry = telemetry,
                height = transportHeight,
            )
        }
        if (ready) Box(modifier = Modifier.size(1.dp).testTag(TimelineTestTags.READY_MARKER))
        val deleting = state.confirmDelete?.let { id -> state.tracks.firstOrNull { it.id == id } }
        if (deleting != null) {
            DeleteTrackDialog(trackName = deleting.name, onConfirm = callbacks.onConfirmDeleteTrack, onCancel = callbacks.onDismissDeleteTrack)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = transportHeight))
        when (state.sheet) {
            TimelineSheet.TempoDialog -> TempoDialog(
                tempo = state.tempo,
                tapCaption = state.tapCaption,
                onTempoDragging = callbacks.onTempoDragging,
                onTempoCommit = callbacks.onTempoCommit,
                onTapTempo = callbacks.onTapTempo,
                onDismiss = { callbacks.onOpenSheet(null) },
            )
            TimelineSheet.MetronomeSettings -> MetronomeSettingsSheet(
                settings = state.metronomeSettings,
                tempo = state.tempo,
                signature = state.timeSignature,
                onClickSound = callbacks.onClickSound,
                onImportCustomSound = callbacks.onImportCustomSound,
                onSubdivision = callbacks.onSubdivision,
                onCycleBeat = callbacks.onCycleBeat,
                onStyle = callbacks.onStyle,
                onDismiss = { callbacks.onOpenSheet(null) },
            )
            TimelineSheet.SignatureMenu, TimelineSheet.GridMenu, TimelineSheet.ZoomMenu, null -> Unit
        }
    }
}

@Composable
private fun RulerPills(state: TimelineUiState, callbacks: TimelineCallbacks, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        TempoPill(tempo = state.tempo, onClick = { callbacks.onOpenSheet(TimelineSheet.TempoDialog) }, enabled = state.loaded)
        TimeSignatureMenu(selected = state.timeSignature, onSelect = callbacks.onTimeSignature, testTag = TimelineTestTags.SIGNATURE_PILL)
    }
}

internal const val READY = "ready"
internal const val LOADING = "loading"
