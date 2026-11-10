package audio.tembra.core.project

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fader's console taper (feature 012, spec FR-003; research R3): piecewise linear in dB between the knots
 * 1.0 → +6, 0.7 → 0, 0.5 → −12, 0.2 → −40, 0.02 → −60, silence below; exact at the knots and invertible.
 */
class FaderTaperTest {

    private fun assertDb(expected: Float, gain: GainDb) = assertTrue(abs(expected - gain.value) < 1e-4f, "expected $expected dB, was ${gain.value}")

    @Test
    fun theKnotsMapExactly() {
        assertDb(6f, FaderTaper.dbOf(1.0f))
        assertDb(0f, FaderTaper.dbOf(0.7f))
        assertDb(-12f, FaderTaper.dbOf(0.5f))
        assertDb(-40f, FaderTaper.dbOf(0.2f))
        assertDb(-60f, FaderTaper.dbOf(0.02f))
        assertEquals(GainDb.Silence, FaderTaper.dbOf(0.019f))
        assertEquals(GainDb.Silence, FaderTaper.dbOf(0f))
        assertEquals(GainDb.Silence, FaderTaper.dbOf(-0.5f))
        assertDb(6f, FaderTaper.dbOf(1.4f))  // past the top: the maximum
    }

    @Test
    fun betweenTheKnotsTheCurveIsLinearInDecibels() {
        assertDb(3f, FaderTaper.dbOf(0.85f))    // halfway between 0.7 and 1.0
        assertDb(-6f, FaderTaper.dbOf(0.6f))    // halfway between 0.5 and 0.7
        assertDb(-26f, FaderTaper.dbOf(0.35f))  // halfway between 0.2 and 0.5
        assertDb(-50f, FaderTaper.dbOf(0.11f))  // halfway between 0.02 and 0.2
    }

    @Test
    fun theInverseReturnsTheThrowWithinATenThousandth() {
        var throwPosition = 0.0201f
        while (throwPosition <= 1.0f) {
            val back = FaderTaper.throwOf(FaderTaper.dbOf(throwPosition))
            assertTrue(abs(back - throwPosition) < 1e-4f, "throw $throwPosition came back as $back")
            throwPosition += 0.0137f
        }
        assertEquals(0.7f, FaderTaper.throwOf(GainDb.Unity))
        assertEquals(1.0f, FaderTaper.throwOf(GainDb(6f)))
        assertEquals(0f, FaderTaper.throwOf(GainDb.Silence))
        assertEquals(0.02f, FaderTaper.throwOf(GainDb(-60f)))
        assertEquals(0.02f, FaderTaper.throwOf(GainDb(-70f)))  // below the last knot: the bottom of the audible range
    }

    @Test
    fun theCurveIsMonotonic() {
        var previous = FaderTaper.dbOf(0.02f).value
        var throwPosition = 0.021f
        while (throwPosition <= 1.0f) {
            val db = FaderTaper.dbOf(throwPosition).value
            assertTrue(db >= previous, "the taper fell from $previous to $db at $throwPosition")
            previous = db
            throwPosition += 0.005f
        }
    }

    @Test
    fun theTicksAreTheLabelledMarksAtTheirThrows() {
        val ticks = FaderTaper.ticks
        assertEquals(listOf("+6", "+3", "0", "−3", "−6", "−10", "−20", "−30", "−40", "−60", "∞"), ticks.map { it.label })
        assertEquals(1.0f, ticks.first().throwPosition)
        assertEquals(0.7f, ticks.single { it.label == "0" }.throwPosition)
        assertEquals(0.02f, ticks.single { it.label == "−60" }.throwPosition)
        assertEquals(0f, ticks.last().throwPosition)
        // The marks descend the throw, like a console's scale.
        assertEquals(ticks.map { it.throwPosition }.sortedDescending(), ticks.map { it.throwPosition })
        // −3 dB sits three quarters of the way from −12 (0.5) to 0 (0.7): linear in dB, so 0.65 of the throw.
        assertTrue(abs(ticks.single { it.label == "−3" }.throwPosition - 0.65f) < 1e-4f)
        assertTrue(abs(ticks.single { it.label == "−20" }.throwPosition - 0.4143f) < 1e-3f)
    }
}
