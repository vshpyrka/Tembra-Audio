package audio.tembra.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The meter's scale and hold (feature 010, spec FR-005; T058): the dB mapping and the 1.5 s hold under a virtual clock. */
class LevelHoldTest {

    @Test
    fun theScaleMapsSilenceToTheFloorAndFullScaleToTheTop() {
        assertEquals(-60f, LevelHold.dbOf(0f))
        assertEquals(-60f, LevelHold.dbOf(0.0001f))
        assertEquals(-20f, LevelHold.dbOf(0.1f), 1e-3f)
        assertEquals(-6.02f, LevelHold.dbOf(0.5f), 1e-2f)
        assertEquals(0f, LevelHold.dbOf(1f))
        assertEquals(0f, LevelHold.dbOf(2f))
        assertEquals(0f, LevelHold.fractionOf(-60f))
        assertEquals(2f / 3f, LevelHold.fractionOf(-20f), 1e-6f)
        assertEquals(0.9f, LevelHold.fractionOf(-6f), 1e-6f)
        assertEquals(1f, LevelHold.fractionOf(0f))
        assertEquals("−20 dB", LevelHold.announce(-20f))
        assertEquals("−60 dB", LevelHold.announce(-60f))
        assertEquals("0 dB", LevelHold.announce(0f))
    }

    @Test
    fun theHoldMarkStaysForOneAndAHalfSecondsThenFalls() {
        val hold = LevelHold()
        assertEquals(-12f, hold.advance(-12f, 0L))
        // Quieter frames inside the hold leave the mark where it was.
        assertEquals(-12f, hold.advance(-40f, 500_000_000L))
        assertEquals(-12f, hold.advance(-40f, 1_400_000_000L))
        // Past 1.5 s it falls at 20 dB/s: 0.5 s later it is 10 dB down, never below the level.
        assertEquals(-22f, hold.advance(-40f, 2_000_000_000L), 1e-3f)
        assertEquals(-40f, hold.advance(-40f, 4_000_000_000L), 1e-3f)
        // A louder frame takes the mark at once and restarts the hold.
        assertEquals(-3f, hold.advance(-3f, 4_100_000_000L))
        assertEquals(-3f, hold.advance(-50f, 5_500_000_000L))
        assertTrue(hold.advance(-50f, 6_000_000_000L) < -3f)
    }

    @Test
    fun resetReturnsToTheFloorAndTheNextLevelIsTakenAsItIs() {
        val hold = LevelHold()
        hold.advance(-10f, 0L)
        hold.reset()
        assertEquals(-60f, hold.holdDb)
        assertEquals(-30f, hold.advance(-30f, 10_000_000_000L))
    }
}
