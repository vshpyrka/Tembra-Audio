package audio.tembra.core.tuner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The noise gate's scale, construction and stored-value rules (feature 006 data-model.md §1, spec FR-002, FR-014). */
class NoiseGateTest {

    @Test
    fun theScaleRunsFromMinusEightyToMinusTwentyInThirtySteps() {
        assertEquals(-80, NoiseGate.MIN_DB)
        assertEquals(-20, NoiseGate.MAX_DB)
        assertEquals(2, NoiseGate.STEP_DB)
        assertEquals(30, NoiseGate.STEPS)
    }

    @Test
    fun offIsStepZeroWithNoLevel() {
        assertEquals(0, NoiseGate.Off.step)
        assertTrue(NoiseGate.Off.isOff)
        assertNull(NoiseGate.Off.levelDb)
        assertEquals(0f, NoiseGate.Off.fraction)
        assertEquals(0, NoiseGate.Off.percent)
    }

    @Test
    fun stepsMapToLevelsFractionsAndPercentages() {
        val first = NoiseGate.ofStep(1)
        assertFalse(first.isOff)
        assertEquals(-78, first.levelDb)
        assertEquals(1f / 30f, first.fraction)
        assertEquals(3, first.percent)

        val middle = NoiseGate.ofStep(15)
        assertEquals(-50, middle.levelDb)
        assertEquals(0.5f, middle.fraction)
        assertEquals(50, middle.percent)

        val last = NoiseGate.ofStep(30)
        assertEquals(-20, last.levelDb)
        assertEquals(1f, last.fraction)
        assertEquals(100, last.percent)
    }

    @Test
    fun ofStepCoercesIntoTheScale() {
        assertEquals(NoiseGate.Off, NoiseGate.ofStep(-3))
        assertEquals(NoiseGate.ofStep(30), NoiseGate.ofStep(31))
        assertEquals(NoiseGate.ofStep(30), NoiseGate.ofStep(1000))
    }

    @Test
    fun ofLevelDbAcceptsOnlyLevelsOfTheScale() {
        assertNull(NoiseGate.ofLevelDb(-90))
        assertNull(NoiseGate.ofLevelDb(-80))
        assertNull(NoiseGate.ofLevelDb(-79))
        assertEquals(-78, NoiseGate.ofLevelDb(-78)?.levelDb)
        assertEquals(-50, NoiseGate.ofLevelDb(-50)?.levelDb)
        assertEquals(-20, NoiseGate.ofLevelDb(-20)?.levelDb)
        assertNull(NoiseGate.ofLevelDb(-19))
        assertNull(NoiseGate.ofLevelDb(0))
    }

    @Test
    fun aLevelBetweenTwoStepsGoesToTheQuieterOne() {
        assertEquals(-52, NoiseGate.ofLevelDb(-51)?.levelDb)
        assertEquals(-78, NoiseGate.ofLevelDb(-77)?.levelDb)
        assertEquals(-22, NoiseGate.ofLevelDb(-21)?.levelDb)
    }

    @Test
    fun aStoredValueThatIsAbsentOrOutsideTheScaleIsOff() {
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(null))
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(-90))
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(-80))
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(-79))
        assertEquals(-78, NoiseGate.fromStored(-78).levelDb)
        assertEquals(-52, NoiseGate.fromStored(-51).levelDb)
        assertEquals(-20, NoiseGate.fromStored(-20).levelDb)
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(-19))
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(0))
        assertEquals(NoiseGate.Off, NoiseGate.fromStored(7))
    }

    @Test
    fun aMeasuredLevelMapsOntoTheTrack() {
        assertEquals(0f, NoiseGate.fractionOfLevel(-100f))
        assertEquals(0f, NoiseGate.fractionOfLevel(-80f))
        assertEquals(0.5f, NoiseGate.fractionOfLevel(-50f))
        assertEquals(1f, NoiseGate.fractionOfLevel(-20f))
        assertEquals(1f, NoiseGate.fractionOfLevel(0f))
    }

    @Test
    fun aLevelAndAGateAtTheSameDecibelsShareOnePlaceOnTheTrack() {
        for (gate in NoiseGate.CheckLevels) {
            assertEquals(gate.fraction, NoiseGate.fractionOfLevel(gate.levelDb!!.toFloat()))
        }
    }

    @Test
    fun theCheckLevelsAreMinusSixtyFiftyAndForty() {
        assertEquals(listOf(-60, -50, -40), NoiseGate.CheckLevels.map { it.levelDb })
        assertEquals(listOf(10, 15, 20), NoiseGate.CheckLevels.map { it.step })
    }
}
