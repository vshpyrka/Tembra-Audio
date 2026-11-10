package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A track's pan as a whole percentage per side (feature 012, spec FR-006 to FR-008; data-model.md §1). */
class PanTest {

    @Test
    fun theReadoutIsCOrASideAndAPercentage() {
        assertEquals("C", Pan.Centre.readout)
        assertEquals("C", Pan(0).readout)
        assertEquals("L 35", Pan(-35).readout)
        assertEquals("R 20", Pan(20).readout)
        assertEquals("L 100", Pan(-100).readout)
        assertEquals("R 100", Pan(Pan.MAX).readout)
    }

    @Test
    fun clampedHoldsTheValueInsideTheRangeAndNeverThrows() {
        assertEquals(Pan(100), Pan.clamped(140))
        assertEquals(Pan(-100), Pan.clamped(-101))
        assertEquals(Pan(-35), Pan.clamped(-35))
        assertEquals(Pan.Centre, Pan.clamped(0))
    }

    @Test
    fun inRangeTellsTheValidatorWhatTheDocumentMayHold() {
        assertTrue(Pan(100).inRange)
        assertTrue(Pan(-100).inRange)
        assertFalse(Pan(101).inRange)
        assertFalse(Pan(-101).inRange)
    }

    @Test
    fun snapToCentreSettlesAReleaseWithinFivePercent() {
        assertEquals(Pan.Centre, Pan(5).snapToCentre())
        assertEquals(Pan.Centre, Pan(-5).snapToCentre())
        assertEquals(Pan(6), Pan(6).snapToCentre())
        assertEquals(Pan(-6), Pan(-6).snapToCentre())
        assertEquals(Pan.Centre, Pan(9).snapToCentre(within = 10))
        assertEquals(Pan(9), Pan(9).snapToCentre(within = 0))
    }

    @Test
    fun toLinearIsThePercentOverOneHundredHeldInsideMinusOneToOne() {
        assertEquals(0f, Pan.Centre.toLinear())
        assertEquals(-0.35f, Pan(-35).toLinear())
        assertEquals(1f, Pan(100).toLinear())
        assertEquals(-1f, Pan(-100).toLinear())
        assertEquals(1f, Pan(140).toLinear())
    }

    @Test
    fun theCentreIsZeroAndTheRangeIsPlusMinusOneHundred() {
        assertEquals(0, Pan.Centre.percent)
        assertEquals(100, Pan.MAX)
    }
}
