package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import audio.tembra.core.project.ClipId
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.core.project.TempoMap
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.timeline.TrackUi

/**
 * One track as the list renders it (contracts/timeline-screen.md "Layouts"): on expanded windows the pinned
 * 200 dp [TrackHeader] beside the lane in one 96 dp row; elsewhere the 56 dp [TrackHeaderCompact] strip above an
 * 88 dp lane, as the phone design draws it. The header's intents are the track's; the lane's are the clips'.
 */
@Composable
internal fun TrackRow(
    track: TrackUi,
    index: Int,
    firstClipIndex: Int,
    state: TimelineUiState,
    layout: TimelineLayout,
    callbacks: TimelineCallbacks,
    modifier: Modifier = Modifier,
    inputTelemetry: InputTelemetry = InputTelemetry { _, _ -> },
) {
    val spacing = TembraTheme.spacing
    val tempoMap = TempoMap(state.tempo, state.timeSignature)
    val liveTake = state.liveTake?.takeIf { it.trackId == track.id }
    val selected = state.selection.trackIdOrNull == track.id
    val isFirst = index == 0
    val isLast = index == state.tracks.lastIndex
    // One row's pitch: what a vertical drag divides by to count the tracks crossed.
    val rowPitch = if (layout.pinnedHeaders) spacing.laneHeightExpanded else HeaderHeight + spacing.laneHeightCompact
    // The open menus live here, above the two row forms, so a window crossing the expanded line keeps them open on the
    // same track or clip (feature 011, spec FR-007); saveable, so scrolling the row away and back keeps them too.
    var trackMenuOpen by rememberSaveable { mutableStateOf(false) }
    var clipMenu by rememberSaveable { mutableStateOf<String?>(null) }
    val menuClip = clipMenu?.let(::ClipId)
    val onMenuClip: (ClipId?) -> Unit = { clipMenu = it?.value }
    if (layout.pinnedHeaders) {
        Row(modifier = modifier.fillMaxWidth().height(spacing.laneHeightExpanded)) {
            TrackHeader(
                track = track,
                index = index,
                selected = selected,
                expanded = state.expandedTrack == track.id,
                isFirst = isFirst,
                isLast = isLast,
                onSelect = { callbacks.onSelectTrack(track.id) },
                onMute = { callbacks.onToggleMute(track.id) },
                onSolo = { callbacks.onToggleSolo(track.id) },
                onGain = { callbacks.onGain(track.id, it) },
                onExpand = { callbacks.onExpandTrack(if (it) track.id else null) },
                onSettings = { callbacks.onTrackSettings(track.id) },
                onAddAudio = { callbacks.onAddAudio(track.id) },
                onMoveUp = { callbacks.onMoveTrack(track.id, -1) },
                onMoveDown = { callbacks.onMoveTrack(track.id, 1) },
                onDelete = { callbacks.onDeleteTrack(track.id) },
                onArm = { callbacks.onArm(track.id) },
                canReorder = state.canMoveOrDeleteTrack,
                inputTelemetry = inputTelemetry,
                onResetClip = { callbacks.onResetClip(track.id) },
                menuOpen = trackMenuOpen,
                onMenuOpen = { trackMenuOpen = it },
            )
            TrackLane(
                track = track,
                index = index,
                firstClipIndex = firstClipIndex,
                viewport = state.viewport,
                selection = state.selection,
                signature = state.timeSignature,
                grid = state.grid,
                height = spacing.laneHeightExpanded,
                onSelectClip = { callbacks.onSelectClip(track.id, it) },
                onClearSelection = callbacks.onClearSelection,
                onZoomAround = callbacks.onZoomAround,
                onScrollBy = callbacks.onScrollBy,
                drag = state.drag,
                rowPitch = rowPitch,
                canSplit = state.canSplit,
                onClipDragStart = callbacks.onClipDragStart,
                onClipDrag = callbacks.onClipDrag,
                onClipDragEnd = callbacks.onClipDragEnd,
                onClipDragCancel = callbacks.onClipDragCancel,
                onSplit = callbacks.onSplit,
                onDeleteClip = callbacks.onDeleteClip,
                menuClip = menuClip,
                onMenuClip = onMenuClip,
                modifier = Modifier.weight(1f),
                tempoMap = tempoMap,
                liveTake = liveTake,
                inputTelemetry = inputTelemetry,
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            TrackHeaderCompact(
                track = track,
                index = index,
                selected = selected,
                isFirst = isFirst,
                isLast = isLast,
                onSelect = { callbacks.onSelectTrack(track.id) },
                onMute = { callbacks.onToggleMute(track.id) },
                onSolo = { callbacks.onToggleSolo(track.id) },
                onSettings = { callbacks.onTrackSettings(track.id) },
                onAddAudio = { callbacks.onAddAudio(track.id) },
                onMoveUp = { callbacks.onMoveTrack(track.id, -1) },
                onMoveDown = { callbacks.onMoveTrack(track.id, 1) },
                onDelete = { callbacks.onDeleteTrack(track.id) },
                onArm = { callbacks.onArm(track.id) },
                onRecord = callbacks.onRecord,
                recordState = state.recordPhase,
                canReorder = state.canMoveOrDeleteTrack,
                inputTelemetry = inputTelemetry,
                onResetClip = { callbacks.onResetClip(track.id) },
                menuOpen = trackMenuOpen,
                onMenuOpen = { trackMenuOpen = it },
                // Just past 0:00, so the playhead at rest falls between the dot and the name.
                nameStart = with(LocalDensity.current) { state.viewport.leadInPx.toFloat().toDp() } + spacing.sm,
            )
            TrackLane(
                track = track,
                index = index,
                firstClipIndex = firstClipIndex,
                viewport = state.viewport,
                selection = state.selection,
                signature = state.timeSignature,
                grid = state.grid,
                height = spacing.laneHeightCompact,
                onSelectClip = { callbacks.onSelectClip(track.id, it) },
                onClearSelection = callbacks.onClearSelection,
                onZoomAround = callbacks.onZoomAround,
                onScrollBy = callbacks.onScrollBy,
                drag = state.drag,
                rowPitch = rowPitch,
                canSplit = state.canSplit,
                onClipDragStart = callbacks.onClipDragStart,
                onClipDrag = callbacks.onClipDrag,
                onClipDragEnd = callbacks.onClipDragEnd,
                onClipDragCancel = callbacks.onClipDragCancel,
                onSplit = callbacks.onSplit,
                onDeleteClip = callbacks.onDeleteClip,
                tempoMap = tempoMap,
                liveTake = liveTake,
                inputTelemetry = inputTelemetry,
                menuClip = menuClip,
                onMenuClip = onMenuClip,
            )
        }
    }
}
