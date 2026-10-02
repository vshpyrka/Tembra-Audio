package audio.tembra.ui.posture

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The fold's span from the window's posture (spec FR-001, data-model.md §1, research R1). */
class TabletopFoldTest {

    private fun hinge(bounds: Rect, vertical: Boolean = false, separating: Boolean = true) =
        HingeInfo(bounds = bounds, isFlat = false, isVertical = vertical, isSeparating = separating, isOccluding = false)

    @Test
    fun noPostureIsNoFold() {
        assertNull(TabletopFold.from(Posture()))
    }

    @Test
    fun aHorizontalHingeOutsideTabletopIsNoFold() {
        assertNull(TabletopFold.from(Posture(isTabletop = false, hingeList = listOf(hinge(Rect(0f, 1038f, 2152f, 1038f))))))
    }

    @Test
    fun tabletopTakesTheHorizontalSeparatingHinge() {
        val posture = Posture(
            isTabletop = true,
            hingeList = listOf(
                hinge(Rect(1000f, 0f, 1010f, 2000f), vertical = true),
                hinge(Rect(0f, 1030f, 2152f, 1046f)),
            ),
        )
        assertEquals(TabletopFold(top = 1030f, bottom = 1046f), TabletopFold.from(posture))
    }

    @Test
    fun aZeroHeightFoldIsKept() {
        val posture = Posture(isTabletop = true, hingeList = listOf(hinge(Rect(0f, 1038f, 2152f, 1038f))))
        assertEquals(TabletopFold(top = 1038f, bottom = 1038f), TabletopFold.from(posture))
    }

    @Test
    fun tabletopWithoutAHorizontalSeparatingHingeIsNoFold() {
        val posture = Posture(
            isTabletop = true,
            hingeList = listOf(hinge(Rect(1000f, 0f, 1010f, 2000f), vertical = true), hinge(Rect(0f, 1030f, 2152f, 1046f), separating = false)),
        )
        assertNull(TabletopFold.from(posture))
    }

    @Test
    fun theBottomIsNeverAboveTheTop() {
        assertFailsWith<IllegalArgumentException> { TabletopFold(top = 10f, bottom = 9f) }
    }
}
