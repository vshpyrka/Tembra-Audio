package audio.tembra.ui.tuner

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The drawn input level between frames (feature 006, contracts/tuner-screen.md §3; spec User Story 3, scenario 1). */
class LevelSmootherTest {

    private val frameNanos = 16_666_667L

    @Test
    fun theFirstValueIsTakenAsItIs() {
        assertEquals(-42f, LevelSmoother().advance(-42f, 1_000_000_000L))
    }

    @Test
    fun aThirtyDecibelRiseIsWithinTwoDecibelsAfterATenthOfASecond() {
        val smoother = LevelSmoother()
        var now = 1_000_000_000L
        var level = smoother.advance(-70f, now)
        var previous = level
        repeat(6) {
            now += frameNanos
            level = smoother.advance(-40f, now)
            assertTrue(level >= previous && level <= -40f, "the level must rise towards the target without passing it: $previous then $level")
            previous = level
        }
        assertTrue(level > -42f, "100 ms after a 30 dB rise the drawn level is $level dB")
    }

    @Test
    fun aFallIsFollowedAsFastAsARise() {
        val smoother = LevelSmoother()
        var now = 1_000_000_000L
        smoother.advance(-30f, now)
        var level = -30f
        repeat(6) {
            now += frameNanos
            level = smoother.advance(-60f, now)
        }
        assertTrue(level < -58f && level >= -60f, "100 ms after a 30 dB fall the drawn level is $level dB")
    }

    @Test
    fun aLongPauseCountsAsOneLongFrame() {
        val smoother = LevelSmoother()
        smoother.advance(-70f, 1_000_000_000L)
        val level = smoother.advance(-40f, 6_000_000_000L)
        val expected = -40f + (-70f - -40f) * exp(-100.0 / 30.0).toFloat()
        assertTrue(abs(level - expected) < 0.01f, "after a five-second pause the level is $level dB, expected $expected")
    }

    @Test
    fun aSteadyLevelStaysPut() {
        val smoother = LevelSmoother()
        var now = 1_000_000_000L
        smoother.advance(-50f, now)
        repeat(10) {
            now += frameNanos
            assertEquals(-50f, smoother.advance(-50f, now))
        }
    }
}
