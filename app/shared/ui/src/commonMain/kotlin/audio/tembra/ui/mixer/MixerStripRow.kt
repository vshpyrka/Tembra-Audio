package audio.tembra.ui.mixer

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.ScrollbarAdapter
import audio.tembra.ui.designsystem.tembraScrollbar
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import kotlinx.collections.immutable.ImmutableList

/**
 * The scrolling row of track strips (feature 012, spec FR-001, FR-023; research R2): a `LazyRow` keyed by track id, 72 dp
 * items, the page margin before the first, a scrollbar while a mouse or trackpad scrolls it (feature 011). The master
 * strip is pinned outside it by the sheet. When the row appears with a selected track its strip is scrolled into view.
 * Every strip's meter draws from the sheet's [feed].
 */
@Composable
internal fun MixerStripRow(
    strips: ImmutableList<StripUi>,
    form: StripForm,
    selectedIndex: Int?,
    callbacks: TimelineCallbacks,
    feed: MeterFeed,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = TembraTheme.spacing
    LaunchedEffect(listState) {
        val index = selectedIndex ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (index in strips.indices && visible.none { it.index == index }) listState.scrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .testTag(MixerTestTags.STRIPS)
            .tembraScrollbar(ScrollbarAdapter(listState), Orientation.Horizontal),
        contentPadding = PaddingValues(start = spacing.pageMargin, end = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(strips, key = { it.id.value }) { strip ->
            MixerStrip(strip = strip, form = form, selected = strip.index == selectedIndex, callbacks = callbacks, feed = feed)
        }
    }
}
