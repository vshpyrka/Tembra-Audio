package audio.tembra.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import audio.tembra.ui.timeline.components.MetronomeSettingsSheet
import audio.tembra.ui.timeline.components.OverviewStrip
import audio.tembra.ui.timeline.components.OverviewStripDefaults
import audio.tembra.ui.timeline.components.PlayheadOverlay
import audio.tembra.ui.timeline.components.SnapSheet
import audio.tembra.ui.timeline.components.TempoDialog
import audio.tembra.ui.timeline.components.TimelineRuler
import audio.tembra.ui.timeline.components.TimelineToolbar
import audio.tembra.ui.timeline.components.TrackList
import audio.tembra.ui.transport.TransportBar
import audio.tembra.ui.transport.TransportCallbacks

/**
 * The stateless Timeline (contracts/timeline-screen.md "Layouts"): toolbar (with the tempo pill at every width, spec
 * FR-036), pinned ruler, the track list under the playhead overlay, the overview strip unless the device hid it, and
 * the transport bar, arranged per [layout]. The ruler carries bars and time only (FR-013, D1): on expanded windows an
 * empty 200 dp slot over the header column makes its canvas start where the lanes do; elsewhere the canvas spans the
 * full width, as the lanes do, so ruler, playhead and clips share one scale (FR-017). The overview strip sits between
 * the lanes and the transport bar, under the lanes only on expanded windows, inset from the window's sides on top of
 * that (FR-015; T151); while [TimelineUiState.overviewStripShown] is false it is not composed and takes no height. The
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
    val headerColumn = if (layout.pinnedHeaders) spacing.headerColumnWidth else 0.dp
    val stripHeight = when {
        !state.overviewStripShown -> 0.dp
        layout == TimelineLayout.Compact || layout == TimelineLayout.Landscape -> OverviewStripDefaults.CompactHeight
        else -> OverviewStripDefaults.Height
    }
    // Keyboard shortcuts need a focused node: the root takes focus once the project is in, never again after (compose-focus-navigation).
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded) rootFocus.requestFocus()
    }
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
            .focusRequester(rootFocus)
            .focusable()
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
                onTempo = { callbacks.onOpenSheet(TimelineSheet.TempoDialog) },
                onGrid = callbacks.onGrid,
                onSnapSheet = { callbacks.onOpenSheet(TimelineSheet.GridMenu) },
                onOverviewStripShown = callbacks.onOverviewStripShown,
                onMetronomeSettings = callbacks.onMetronomeSettings,
            )
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
                leftSlot = if (layout.pinnedHeaders) {
                    { Spacer(Modifier.width(spacing.headerColumnWidth)) }
                } else {
                    null
                },
            )
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
                    modifier = Modifier.fillMaxSize().padding(start = headerColumn),
                )
            }
            if (state.overviewStripShown) {
                // The strip adds its own side inset (the page margin, or the back-gesture zone where wider) to this.
                OverviewStrip(
                    viewport = state.viewport,
                    tracks = state.tracks,
                    playing = state.isPlaying,
                    positionBeats = state.positionBeats,
                    telemetry = telemetry,
                    onScrollTo = callbacks.onScrollTo,
                    onZoomTo = callbacks.onZoomTo,
                    modifier = Modifier.padding(start = headerColumn),
                    height = stripHeight,
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
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = transportHeight + stripHeight))
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
                onTimeSignature = callbacks.onTimeSignature,
                onClickSound = callbacks.onClickSound,
                onImportCustomSound = callbacks.onImportCustomSound,
                onSubdivision = callbacks.onSubdivision,
                onCycleBeat = callbacks.onCycleBeat,
                onStyle = callbacks.onStyle,
                onDismiss = { callbacks.onOpenSheet(null) },
            )
            TimelineSheet.GridMenu -> SnapSheet(grid = state.grid, onGrid = callbacks.onGrid, onDismiss = { callbacks.onOpenSheet(null) })
            null -> Unit
        }
    }
}

internal const val READY = "ready"
internal const val LOADING = "loading"
