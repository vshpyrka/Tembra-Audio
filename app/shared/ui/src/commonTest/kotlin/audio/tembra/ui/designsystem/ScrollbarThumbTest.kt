package audio.tembra.ui.designsystem

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The scrollbar thumb's place (feature 011, FR-014a): along the trailing or bottom edge, as long as the viewport is of
 * the content, and for the timeline's time axis on the lanes only, past the track headers pinned at its start (found in
 * the T125 self-check: the thumb ran under the headers and stopped a header's width short of the end).
 */
class ScrollbarThumbTest {

    private fun adapter(offset: Float, content: Float, viewport: Float, leading: Float = 0f) = object : ScrollbarAdapter {
        override val scrollOffset = offset
        override val contentSize = content
        override val viewportSize = viewport
        override val leadingPx = leading
        override fun scrollBy(delta: Float) = Unit
    }

    private fun thumb(adapter: ScrollbarAdapter, orientation: Orientation, bounds: IntSize) =
        scrollbarThumb(adapter, orientation, bounds, thickness = 6f, inset = 2f, minThumb = 24f)

    @Test
    fun everythingFittingDrawsNoThumb() {
        assertNull(thumb(adapter(0f, 400f, 400f), Orientation.Vertical, IntSize(300, 400)))
    }

    @Test
    fun aVerticalThumbRunsDownTheTrailingEdge() {
        // A quarter of the content shows, scrolled to the end: the thumb is a quarter of the 396 px track, at its end.
        val rect = thumb(adapter(1200f, 1600f, 400f), Orientation.Vertical, IntSize(300, 400))
        assertEquals(Rect(Offset(292f, 2f + 297f), Size(6f, 99f)), rect)
    }

    @Test
    fun theTimeAxisThumbStartsPastThePinnedHeaders() {
        // A 1000 px list whose first 200 px are the headers: the lanes are the 800 px viewport. Scrolled to the end, the
        // thumb ends 2 px from the list's right edge rather than 200 px short of it; at the start it begins past the headers.
        val end = thumb(adapter(2400f, 3200f, 800f, leading = 200f), Orientation.Horizontal, IntSize(1000, 500))!!
        assertEquals(998f, end.right)
        val start = thumb(adapter(0f, 3200f, 800f, leading = 200f), Orientation.Horizontal, IntSize(1000, 500))!!
        assertEquals(202f, start.left)
        assertEquals(500f - 2f - 6f, start.top)
    }
}
