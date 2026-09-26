package audio.tembra.ui.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure gauge geometry and the row layout behind the meter (data-model.md §8). */
class MeterGeometryTest {

    private val geometry = MeterGeometry(width = 400f)

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun theDesignViewBoxScalesFromTheWidth() {
        assertNear(230f, geometry.height)
        assertNear(200f, geometry.pivotX)
        assertNear(210f, geometry.pivotY)
        assertNear(160f, geometry.outerRadius)
        val half = MeterGeometry(200f)
        assertNear(115f, half.height)
        assertNear(80f, half.outerRadius)
    }

    @Test
    fun needleAnglesAreOnePointEightDegreesPerCentAndClamp() {
        assertEquals(0f, geometry.needleAngleDegrees(0.0))
        assertNear(45f, geometry.needleAngleDegrees(25.0))
        assertNear(-90f, geometry.needleAngleDegrees(-50.0))
        assertNear(90f, geometry.needleAngleDegrees(80.0))
        assertNear(-90f, geometry.needleAngleDegrees(-120.0))
        assertNear(5.4f, geometry.inTuneHalfAngle)
    }

    @Test
    fun pointsFollowTheArc() {
        val (tipX, tipY) = geometry.needleTip(0.0)
        assertNear(200f, tipX)
        assertNear(38f, tipY)
        val (leftX, leftY) = geometry.endMarkStart(-90f)
        assertNear(40f, leftX)
        assertNear(210f, leftY)
        val (rightX, _) = geometry.endMarkEnd(90f)
        assertNear(378f, rightX)
        val (midX, midY) = geometry.needlePoint(0.0, 0.5f)
        assertNear(200f, midX)
        assertNear(124f, midY)
    }

    @Test
    fun theLayoutStacksPillNoteArcReadoutAndLabels() {
        val layout = MeterLayout(width = 340f)
        assertTrue(layout.pillCenter.second < layout.noteBaseline)
        assertTrue(layout.noteBaseline <= layout.arcTop)
        assertTrue(layout.flatLabelCenter.first < layout.sharpLabelCenter.first)
        assertTrue(layout.flatLabelCenter.second > layout.arcTop + layout.arcHeight)
        assertTrue(layout.sharpLabelCenter.second > layout.arcTop + layout.arcHeight)
        assertNear(340f / 1.8f, layout.arcHeight)
        assertNear(layout.arcHeight + layout.textRowsHeight, layout.height)
        assertTrue(layout.geometry.width <= 340f)
        val large = MeterLayout(width = 340f, fontScale = 2f)
        val dense = MeterLayout(width = 340f, density = 3f)
        assertTrue(dense.textRowsHeight > layout.textRowsHeight)
        assertEquals(layout.arcHeight, dense.arcHeight)
        assertTrue(large.textRowsHeight > layout.textRowsHeight)
        assertEquals(layout.arcHeight, large.arcHeight)
    }

    @Test
    fun smoothingApproachesTheTargetWithAnEightyMillisecondTimeConstant() {
        val after80 = MeterGeometry.smoothAngle(current = 0f, target = 90f, dtMillis = 80)
        assertTrue(after80 in 55f..70f, "after 80 ms the needle is at $after80")
        assertNear(90f, MeterGeometry.smoothAngle(0f, 90f, 1000), 0.5f)
        assertEquals(0f, MeterGeometry.smoothAngle(0f, 90f, 0))
    }
}
