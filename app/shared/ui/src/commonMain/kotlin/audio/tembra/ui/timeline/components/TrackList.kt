package audio.tembra.ui.timeline.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.ScrollbarAdapter
import audio.tembra.ui.designsystem.tembraScrollbar
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
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> onMeasured((size.width - headerWidthPx).toDouble(), density.density.toDouble()) }
            .testTag(TimelineTestTags.TRACK_LIST)
            // Scrollbars while a mouse or trackpad scrolls (feature 011, spec FR-014a): the tracks, and time along the foot.
            .tembraScrollbar(ScrollbarAdapter(listState), Orientation.Vertical)
            .tembraScrollbar(TimelineScrollbarAdapter(state.viewport, state.lengthBeats) { onScrollBy(it.toDouble()) }, Orientation.Horizontal),
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
}
