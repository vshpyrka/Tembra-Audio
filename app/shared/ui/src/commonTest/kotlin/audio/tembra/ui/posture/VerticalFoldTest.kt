package audio.tembra.ui.posture

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The book-posture hinge from the window's posture (design iteration D1, research R8). */
class VerticalFoldTest {

    private fun hinge(bounds: Rect, vertical: Boolean, separating: Boolean = true) =
        HingeInfo(bounds = bounds, isFlat = !separating, isVertical = vertical, isSeparating = separating, isOccluding = false)

    @Test
    fun noPostureIsNoFold() {
        assertNull(VerticalFold.from(Posture()))
    }

    @Test
    fun aSeparatingVerticalHingeIsTheFold() {
        val posture = Posture(isTabletop = false, hingeList = listOf(hinge(Rect(1038f, 0f, 1038f, 2152f), vertical = true)))
        assertEquals(VerticalFold(left = 1038f, right = 1038f), VerticalFold.from(posture))
    }

    @Test
    fun aFlatOrHorizontalHingeIsNoVerticalFold() {
        assertNull(VerticalFold.from(Posture(hingeList = listOf(hinge(Rect(1038f, 0f, 1038f, 2152f), vertical = true, separating = false)))))
        assertNull(VerticalFold.from(Posture(isTabletop = true, hingeList = listOf(hinge(Rect(0f, 1038f, 2152f, 1038f), vertical = false)))))
    }

    @Test
    fun theRightEdgeIsNeverLeftOfTheLeft() {
        assertFailsWith<IllegalArgumentException> { VerticalFold(left = 10f, right = 9f) }
    }
}
