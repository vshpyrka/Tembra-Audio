package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.tracksettings.TrackSettingsInlineRow

/**
 * The vertically scrolling list of [TrackRow]s (spec FR-016; contracts/timeline-screen.md): keyed by track id so a
 * reorder moves rows instead of rebuilding them. Its lane width (the list minus the pinned header column) is reported
 * through [TimelineCallbacks.onViewportMeasured] whenever it changes, which is what the viewport maps beats to. A
 * track just added is selected (009 FR-019) and lands below the visible rows on a phone: the list scrolls it into
 * view, so its arm toggle is at hand (feature 010, FR-008; the recording journey arms it right after "+ Track").
 */
@Composable
internal fun TrackList(
    state: TimelineUiState,
    layout: TimelineLayout,
    callbacks: TimelineCallbacks,
    modifier: Modifier = Modifier,
    inputTelemetry: InputTelemetry = InputTelemetry { _, _ -> },
) {
    val density = LocalDensity.current
    val headerWidthPx = if (layout.pinnedHeaders) with(density) { TembraTheme.spacing.headerColumnWidth.toPx() } else 0f
    val onMeasured by rememberUpdatedState(callbacks.onViewportMeasured)
    val firstClipIndices = remember(state.tracks) {
        var running = 0
        state.tracks.map { track -> running.also { running += track.clips.size } }
    }
    val listState = rememberLazyListState()
    val selectedTrack = state.selection.trackIdOrNull
    var knownTracks by remember { mutableIntStateOf(state.tracks.size) }
    LaunchedEffect(state.tracks.size) {
        val grew = state.tracks.size > knownTracks
        knownTracks = state.tracks.size
        // An instant scroll, not an animated one: a tap on the new track's header during the animation is cancelled
        // when the row slides out from under the finger (the recording journey lost its arm tap once in 49 runs).
        if (grew && selectedTrack != null && state.tracks.lastOrNull()?.id == selectedTrack) listState.scrollToItem(state.tracks.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> onMeasured((size.width - headerWidthPx).toDouble(), density.density.toDouble()) }
            .testTag(TimelineTestTags.TRACK_LIST),
    ) {
        itemsIndexed(state.tracks, key = { _, track -> track.id.value }) { index, track ->
            TrackRow(
                track = track,
                index = index,
                firstClipIndex = firstClipIndices[index],
                state = state,
                layout = layout,
                callbacks = callbacks,
                inputTelemetry = inputTelemetry,
            )
            // The expanded layout's inline settings under the open track (spec FR-041); the chevron collapses it.
            if (layout.inlineSettings && state.expandedTrack == track.id) {
                TrackSettingsInlineRow(trackId = track.id, onCollapse = { callbacks.onExpandTrack(null) })
            }
        }
    }
}
