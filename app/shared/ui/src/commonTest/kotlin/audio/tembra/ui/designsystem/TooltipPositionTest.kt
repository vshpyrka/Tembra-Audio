package audio.tembra.ui.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a tooltip goes (feature 011, desktop quality guideline T-Hover_Parity, found in the T125 self-check): above its
 * control unless that lands under what the system draws over the window's top edge (a desktop window's caption bar, the
 * status bar), in which case below. The limit is read when the popup is placed.
 */
class TooltipPositionTest {

    private val popup = IntSize(160, 54)
    private val window = IntSize(2880, 1800)

    /** The platform's "above" placement: the popup's bottom 6 px over the anchor. */
    private val above = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            IntOffset(anchorBounds.left, anchorBounds.top - popupContentSize.height - 6)
    }

    /** The platform's "below" placement: 6 px under the anchor. */
    private val below = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            IntOffset(anchorBounds.left, anchorBounds.bottom + 6)
    }

    private fun place(anchor: IntRect, topInset: Int) =
        InsetAwareTooltipPosition(above, below, topLimitPx = { topInset }).calculatePosition(anchor, window, LayoutDirection.Ltr, popup)

    @Test
    fun aControlWellBelowTheTopEdgeShowsItsTooltipAbove() {
        assertEquals(IntOffset(600, 1540), place(IntRect(600, 1600, 666, 1666), topInset = 72))
    }

    @Test
    fun aControlRightUnderTheCaptionBarShowsItsTooltipBelow() {
        // The toolbar row of a desktop window: above would put the tooltip at y 18, under the 72 px caption bar.
        assertEquals(IntOffset(220, 150 + 6), place(IntRect(220, 78, 286, 150), topInset = 72))
    }

    @Test
    fun withNoInsetOnlyTheWindowEdgeCounts() {
        assertEquals(IntOffset(220, 18), place(IntRect(220, 78, 286, 150), topInset = 0))
    }

    @Test
    fun theLimitIsReadWhenThePopupIsPlaced() {
        var limit = 0
        val position = InsetAwareTooltipPosition(above, below, topLimitPx = { limit })
        val anchor = IntRect(220, 78, 286, 150)
        assertEquals(IntOffset(220, 18), position.calculatePosition(anchor, window, LayoutDirection.Ltr, popup))
        limit = 72
        assertEquals(IntOffset(220, 156), position.calculatePosition(anchor, window, LayoutDirection.Ltr, popup))
    }
}
