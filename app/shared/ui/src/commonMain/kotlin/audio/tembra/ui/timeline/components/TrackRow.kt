package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.theme.TembraTheme
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
) {
    val spacing = TembraTheme.spacing
    val selected = state.selection.trackIdOrNull == track.id
    val isFirst = index == 0
    val isLast = index == state.tracks.lastIndex
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
            )
            TrackLane(
                track = track,
                index = index,
                firstClipIndex = firstClipIndex,
                viewport = state.viewport,
                selection = state.selection,
                signature = state.timeSignature,
                height = spacing.laneHeightExpanded,
                onSelectClip = { callbacks.onSelectClip(track.id, it) },
                onClearSelection = callbacks.onClearSelection,
                modifier = Modifier.weight(1f),
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
            )
            TrackLane(
                track = track,
                index = index,
                firstClipIndex = firstClipIndex,
                viewport = state.viewport,
                selection = state.selection,
                signature = state.timeSignature,
                height = spacing.laneHeightCompact,
                onSelectClip = { callbacks.onSelectClip(track.id, it) },
                onClearSelection = callbacks.onClearSelection,
            )
        }
    }
}
