package audio.tembra.ui.timeline.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.Snap
import audio.tembra.ui.designsystem.ScrollbarAdapter
import audio.tembra.ui.designsystem.tembraScrollbar
import audio.tembra.ui.drop.AudioDropState
import audio.tembra.ui.drop.DropCopy
import audio.tembra.ui.drop.DropHoverBanner
import audio.tembra.ui.drop.DropPreview
import audio.tembra.ui.drop.DropZone
import audio.tembra.ui.drop.audioDropTarget
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.tracksettings.TrackSettingsInlineRow

/**
 * The vertically scrolling list of [TrackRow]s (spec FR-016; contracts/timeline-screen.md): keyed by track id so a
 * reorder moves rows instead of rebuilding them. Its lane width (the list minus the pinned header column) is reported
 * through [TimelineCallbacks.onViewportMeasured] whenever it changes, which is what the viewport maps beats to.
 */
@Composable
internal fun TrackList(
    state: TimelineUiState,
    layout: TimelineLayout,
    callbacks: TimelineCallbacks,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val headerWidthPx = if (layout.pinnedHeaders) with(density) { TembraTheme.spacing.headerColumnWidth.toPx() } else 0f
    val onMeasured by rememberUpdatedState(callbacks.onViewportMeasured)
    val firstClipIndices = remember(state.tracks) {
        var running = 0
        state.tracks.map { track -> running.also { running += track.clips.size } }
    }
    val listState = rememberLazyListState()
    val onScrollBy by rememberUpdatedState(callbacks.onScrollBy)
    // Files dropped on a track's row join that track, below the rows they become new tracks (feature 011, FR-026): the
    // zone is the row under the pointer, the beat the snapped one under it. Where the platform does not say where the
    // drag is (iOS), the space at the playhead.
    val currentState by rememberUpdatedState(state)
    val drops = remember(listState, headerWidthPx) {
        AudioDropState(
            zoneAt = { position ->
                val row = position?.let { listState.layoutInfo.visibleItemsInfo.firstOrNull { item -> it.y >= item.offset && it.y < item.offset + item.size } }
                (row?.key as? String)?.let { DropZone.TrackLane(TrackId(it)) } ?: DropZone.TimelineSpace
            },
            beatsAt = { position ->
                val now = currentState
                // Without a position (iOS, research R12 follow-up) the files go where the playhead stands.
                val beats = position?.let { now.viewport.beatsAt((it.x - headerWidthPx).toDouble().coerceAtLeast(0.0)) } ?: now.positionBeats
                Snap.snap(beats, now.grid, now.timeSignature).coerceAtLeastZero()
            },
        )
    }
    Box(modifier = modifier.fillMaxSize().audioDropTarget(drops, callbacks.onDrop)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> onMeasured((size.width - headerWidthPx).toDouble(), density.density.toDouble()) }
                .testTag(TimelineTestTags.TRACK_LIST)
                // Scrollbars while a mouse or trackpad scrolls (feature 011, spec FR-014a): the tracks, and time along the foot.
                .tembraScrollbar(ScrollbarAdapter(listState), Orientation.Vertical)
                .tembraScrollbar(TimelineScrollbarAdapter(state.viewport, state.lengthBeats, headerWidthPx) { onScrollBy(it.toDouble()) }, Orientation.Horizontal),
        ) {
            itemsIndexed(state.tracks, key = { _, track -> track.id.value }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    firstClipIndex = firstClipIndices[index],
                    state = state,
                    layout = layout,
                    callbacks = callbacks,
                )
                // The expanded layout's inline settings under the open track (spec FR-041); the chevron collapses it.
                if (layout.inlineSettings && state.expandedTrack == track.id) {
                    TrackSettingsInlineRow(trackId = track.id, onCollapse = { callbacks.onExpandTrack(null) })
                }
            }
        }
        drops.hover?.let { hover -> DropHoverBanner(hoverText(hover.preview, state), Modifier.align(Alignment.TopCenter)) }
    }
}

/** What the hover banner says: "Add to Drums at bar 5", "New track" (feature 011, spec FR-026). */
private fun hoverText(preview: DropPreview, state: TimelineUiState): String = when (preview) {
    is DropPreview.AddToTrack -> DropCopy.addToTrack(state.tracks.firstOrNull { it.id == preview.trackId }?.name.orEmpty(), preview, state.timeSignature)
    is DropPreview.NewTracks -> DropCopy.newTrack
    DropPreview.NewProject -> DropCopy.newProject
}
