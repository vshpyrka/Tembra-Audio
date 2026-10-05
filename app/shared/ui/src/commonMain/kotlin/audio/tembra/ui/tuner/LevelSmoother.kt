package audio.tembra.ui.tuner

import kotlin.math.exp

/**
 * The drawn input level between frames (feature 006 research R10): an approach in decibels with a 30 ms time
 * constant. The engine refreshes the level 47 times a second and the display draws 60 to 120, so this only takes the
 * steps out; the first value is shown as it is.
 */
internal class LevelSmoother {
    private var levelDb = Float.NaN
    private var lastNanos = 0L

    fun advance(targetDb: Float, frameNanos: Long): Float {
        val dtMillis = if (lastNanos == 0L) 0L else ((frameNanos - lastNanos) / 1_000_000L).coerceIn(0L, MAX_STEP_MILLIS)
        lastNanos = frameNanos
        levelDb = if (levelDb.isNaN()) targetDb else (targetDb + (levelDb - targetDb) * exp(-dtMillis / SMOOTHING_MILLIS)).toFloat()
        return levelDb
    }

    private companion object {
        const val SMOOTHING_MILLIS = 30.0

        /** A long pause (the app was hidden) is treated as one long frame. */
        const val MAX_STEP_MILLIS = 100L
    }
}
