package audio.tembra.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.FocusMemory
import audio.tembra.ui.designsystem.focusRestoringRoot
import audio.tembra.ui.drop.AudioDropState
import audio.tembra.ui.drop.DropCopy
import audio.tembra.ui.drop.DropHoverBanner
import audio.tembra.ui.drop.DropZone
import audio.tembra.ui.drop.audioDropTarget
import audio.tembra.ui.mixer.MixerSheet
import audio.tembra.ui.platform.LocalTrackpadPinchSource
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.shortcuts.RegisterShortcuts
import audio.tembra.ui.shortcuts.ShortcutTargets
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.DeleteTrackDialog
import audio.tembra.ui.timeline.components.EmptyTimeline
import audio.tembra.ui.timeline.components.MetronomeSettingsSheet
import audio.tembra.ui.timeline.components.OverviewStrip
import audio.tembra.ui.timeline.components.OverviewStripDefaults
import audio.tembra.ui.timeline.components.PlayheadMask
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
 * the lanes and the transport bar, under the lanes only on expanded windows, and inset on each side by the page margin
 * plus whatever part of the back-gesture zone still reaches it past that header column (FR-015; T151, T153); while
 * [TimelineUiState.overviewStripShown] is false it is not composed and takes no height. The root is tagged
 * `screen.timeline` with `stateDescription` "ready" once every visible overview is drawn
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
    inputTelemetry: InputTelemetry = InputTelemetry { _, _ -> },
) {
    val spacing = TembraTheme.spacing
    val daw = TembraTheme.daw
    val density = LocalDensity.current
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
    // Focus returns to the same track or control after a window change rebuilds the rows (feature 011, FR-007).
    val focusMemory = remember { FocusMemory() }
    // The iPad's trackpad pinch arrives from the shell's bridge in root pixels (feature 011, research R9): it zooms
    // around its focus, measured from the lanes' left edge (written at layout, read only by the collector).
    val lanesLeft = remember { mutableFloatStateOf(0f) }
    // The lanes box's height, written at layout, sizes the mixer sheet (feature 012).
    var lanesHeight by remember { mutableStateOf(0.dp) }
    val onLanesMeasured by rememberUpdatedState(callbacks.onLanesMeasured)
    val playheadMask = remember { PlayheadMask() }
    val pinchSource = LocalTrackpadPinchSource.current
    val onZoomAround by rememberUpdatedState(callbacks.onZoomAround)
    // The ⌘ chords' menu commands on iPad (feature 011, spec FR-018a) act on the state showing now.
    val currentState by rememberUpdatedState(state)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val menuTargets = remember { TimelineKeyTargets({ currentState }, { currentCallbacks }) }
    RegisterShortcuts(ShortcutTargets(timeline = menuTargets))
    LaunchedEffect(pinchSource) {
        pinchSource.pinches.collect { pinch ->
            val x = pinch.focus.x - lanesLeft.floatValue
            if (x >= 0f) PerfTrace.animation(PerfSection.TIMELINE_ZOOM) { onZoomAround(x.toDouble(), pinch.scale.toDouble()) }
        }
    }
    val transportCallbacks = TransportCallbacks(
        onPlayPause = callbacks.onPlayPause,
        onSkipToStart = callbacks.onSkipToStart,
        onStepBar = callbacks.onStepBar,
        onMasterVolume = callbacks.onMasterVolume,
        onMetronomeToggle = callbacks.onMetronomeToggle,
        onMetronomeSettings = callbacks.onMetronomeSettings,
        onRecord = callbacks.onRecord,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(daw.canvasLevel0)
            .testTag(TimelineTestTags.ROOT)
            .semantics { stateDescription = if (ready) READY else LOADING }
            .focusRestoringRoot(focusMemory)
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
                onFullScreen = callbacks.onFullScreen,
                onMetronomeSettings = callbacks.onMetronomeSettings,
                onMonitorToggle = callbacks.onMonitorToggle,
                onMixerToggle = callbacks.onMixerToggle,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates -> lanesLeft.floatValue = coordinates.positionInRoot().x + headerColumn.toPx(density) }
                    // The lanes box's height sizes the mixer sheet's anchors (feature 012, spec FR-016, FR-021).
                    .onSizeChanged { size ->
                        val height = with(density) { size.height.toDp() }
                        lanesHeight = height
                        onLanesMeasured(layout, height)
                    },
            ) {
                if (state.isEmpty) {
                    // An empty project takes dropped files as its first tracks, from the start (feature 011, FR-026).
                    val drops = remember { AudioDropState(zoneAt = { DropZone.TimelineSpace }, beatsAt = { Beats.Zero }) }
                    Box(modifier = Modifier.fillMaxSize().audioDropTarget(drops, callbacks.onDrop)) {
                        EmptyTimeline(onAddTrack = callbacks.onAddTrack, onAddAudio = callbacks.onAddAudioAsNewTrack)
                        if (drops.hover != null) DropHoverBanner(DropCopy.newTrack, Modifier.align(Alignment.TopCenter))
                    }
                } else {
                    TrackList(state = state, layout = layout, callbacks = callbacks, inputTelemetry = inputTelemetry, playheadMask = playheadMask)
                }
                PlayheadOverlay(
                    viewport = state.viewport,
                    signature = state.timeSignature,
                    playing = state.isPlaying,
                    positionBeats = state.positionBeats,
                    telemetry = telemetry,
                    modifier = Modifier.fillMaxSize().padding(start = headerColumn),
                    mask = playheadMask,
                )
                // The mixer sheet slides up over the lanes, inside this box, so the strip and the transport bar stay in reach (feature 012, FR-016).
                MixerSheet(ui = state.mixer, callbacks = callbacks, lanesHeight = lanesHeight)
            }
            if (state.overviewStripShown) {
                // The strip adds its own side inset to this: the page margin, plus the part of the back-gesture zone that
                // still reaches it. The header column is consumed as well as padded: the zone lies inside the 200 dp
                // column, so the strip starts the margin after it, not the margin plus the zone (T153).
                OverviewStrip(
                    viewport = state.viewport,
                    tracks = state.tracks,
                    playing = state.isPlaying,
                    positionBeats = state.positionBeats,
                    telemetry = telemetry,
                    onScrollTo = callbacks.onScrollTo,
                    onZoomTo = callbacks.onZoomTo,
                    modifier = Modifier.padding(start = headerColumn).consumeWindowInsets(PaddingValues(start = headerColumn)),
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
        // Above the transport bar, the strip and, while it is open, the mixer sheet (feature 012, contracts/mixer-ui.md).
        val sheetHeight = if (state.mixer.open) state.mixer.sheetHeight else 0.dp
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = transportHeight + stripHeight + sheetHeight)) { data ->
            // The action as a tagged button: the denied message's "Open settings" / "Allow" (feature 010, FR-020) is a journey anchor.
            val label = data.visuals.actionLabel
            Snackbar(
                action = label?.let {
                    {
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                            modifier = Modifier.testTag(TimelineTestTags.DENIED_ACTION),
                        ) { Text(it) }
                    }
                },
            ) { Text(data.visuals.message) }
        }
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
                countIn = state.countIn,
                countInEnabled = state.canEditCountIn,
                onCountIn = callbacks.onCountIn,
            )
            TimelineSheet.GridMenu -> SnapSheet(grid = state.grid, onGrid = callbacks.onGrid, onDismiss = { callbacks.onOpenSheet(null) })
            null -> Unit
        }
    }
}

internal const val READY = "ready"
internal const val LOADING = "loading"

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density): Float = with(density) { toPx() }
