package audio.tembra.ui.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * What a [tembraScrollbar] needs from a scrolling container (feature 011, research R18): how far it is scrolled, how
 * long its content and its viewport are (pixels, along the scrollbar's axis), and a way to move it by a drag of the
 * thumb. Lazy containers estimate their content from the items laid out so far.
 */
internal interface ScrollbarAdapter {
    val scrollOffset: Float
    val contentSize: Float
    val viewportSize: Float

    /** Scrolls the content by [delta] pixels (positive moves later in the content). */
    fun scrollBy(delta: Float)
}

/** A plain `verticalScroll`/`horizontalScroll` container. */
internal fun ScrollbarAdapter(state: ScrollState): ScrollbarAdapter = ScrollStateAdapter(state)

/** A `LazyColumn`/`LazyRow`: the content's length from the average size of the items laid out. */
internal fun ScrollbarAdapter(state: LazyListState): ScrollbarAdapter = LazyListAdapter(state)

/** A `LazyVerticalGrid`: rows of [columns] items, each row as tall as the laid-out rows on average. */
internal fun ScrollbarAdapter(state: LazyGridState, columns: Int): ScrollbarAdapter = LazyGridAdapter(state, columns)

/** A plain scroll container with a scrollbar: the scrollbar before the scroll, on the same viewport. */
internal fun Modifier.verticalScrollWithScrollbar(state: ScrollState): Modifier =
    tembraScrollbar(ScrollbarAdapter(state), Orientation.Vertical).verticalScroll(state)

// Data classes, so an adapter rebuilt on recomposition equals the last one and leaves the scrollbar's node alone.
private data class ScrollStateAdapter(val state: ScrollState) : ScrollbarAdapter {
    override val scrollOffset: Float get() = state.value.toFloat()
    override val contentSize: Float get() = (state.maxValue + state.viewportSize).toFloat()
    override val viewportSize: Float get() = state.viewportSize.toFloat()
    override fun scrollBy(delta: Float) {
        state.dispatchRawDelta(delta)
    }
}

private data class LazyListAdapter(val state: LazyListState) : ScrollbarAdapter {
    private val averageItem: Float
        get() = state.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }?.let { items -> items.sumOf { it.size }.toFloat() / items.size } ?: 0f
    override val scrollOffset: Float get() = state.firstVisibleItemIndex * averageItem + state.firstVisibleItemScrollOffset
    override val contentSize: Float get() = state.layoutInfo.totalItemsCount * averageItem
    override val viewportSize: Float
        get() = state.layoutInfo.let { (it.viewportEndOffset - it.viewportStartOffset).toFloat() }
    override fun scrollBy(delta: Float) {
        state.dispatchRawDelta(delta)
    }
}

private data class LazyGridAdapter(val state: LazyGridState, val columns: Int) : ScrollbarAdapter {
    private val averageRow: Float
        get() = state.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }?.let { items -> items.sumOf { it.size.height }.toFloat() / items.size } ?: 0f
    override val scrollOffset: Float get() = (state.firstVisibleItemIndex / columns) * averageRow + state.firstVisibleItemScrollOffset
    override val contentSize: Float get() = ((state.layoutInfo.totalItemsCount + columns - 1) / columns) * averageRow
    override val viewportSize: Float
        get() = state.layoutInfo.let { (it.viewportEndOffset - it.viewportStartOffset).toFloat() }
    override fun scrollBy(delta: Float) {
        state.dispatchRawDelta(delta)
    }
}
