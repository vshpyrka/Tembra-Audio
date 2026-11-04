package audio.tembra.ui.timeline

import kotlin.math.log10
import kotlin.math.max

/**
 * The input meter's scale and peak hold (feature 010, spec FR-005; contracts/timeline-recording-ui.md "InputMeter"):
 * a linear peak maps to decibels on the −60 dB (silence) to 0 dB (full scale) scale, and [advance] keeps the highest
 * level seen as the hold mark for [HOLD_NANOS] before letting it fall at [FALL_DB_PER_SECOND] until the level catches
 * it. Pure and clock-free: the caller passes the frame time, so a test drives it with a virtual clock.
 */
internal class LevelHold {
    /** The hold mark in decibels; [FLOOR_DB] until the first level. */
    var holdDb: Float = FLOOR_DB
        private set
    private var heldSinceNanos = Long.MIN_VALUE

    /** Feeds this frame's [levelDb] at [frameNanos]; returns the hold mark after it. */
    fun advance(levelDb: Float, frameNanos: Long): Float {
        val level = levelDb.coerceIn(FLOOR_DB, CEILING_DB)
        if (heldSinceNanos == Long.MIN_VALUE || level >= holdDb) {
            holdDb = level
            heldSinceNanos = frameNanos
        } else if (frameNanos - heldSinceNanos > HOLD_NANOS) {
            val falling = (frameNanos - heldSinceNanos - HOLD_NANOS) / 1e9 * FALL_DB_PER_SECOND
            holdDb = max(level, (holdDb - falling).toFloat())
            // The fall is measured from the moment it begins: the mark keeps the time it started falling.
            heldSinceNanos = frameNanos - HOLD_NANOS
        }
        return holdDb
    }

    fun reset() {
        holdDb = FLOOR_DB
        heldSinceNanos = Long.MIN_VALUE
    }

    companion object {
        const val FLOOR_DB = -60f
        const val CEILING_DB = 0f
        /** The body colour reaches −6 dB; above it the warning colour (spec FR-005). */
        const val WARNING_FROM_DB = -6f
        const val HOLD_NANOS = 1_500_000_000L
        const val FALL_DB_PER_SECOND = 20.0

        /** A linear peak (0 to 1) in decibels, clamped to the scale; silence is the floor. */
        fun dbOf(peakLinear: Float): Float = if (peakLinear <= 0f) FLOOR_DB else (20.0 * log10(peakLinear.toDouble())).toFloat().coerceIn(FLOOR_DB, CEILING_DB)

        /** Where [db] sits on the track: 0 at the floor, 1 at full scale, linear in decibels. */
        fun fractionOf(db: Float): Float = ((db - FLOOR_DB) / (CEILING_DB - FLOOR_DB)).coerceIn(0f, 1f)

        /** The announced value: "−20 dB" with the typographic minus, "−60 dB" for silence. */
        fun announce(db: Float): String {
            val rounded = kotlin.math.round(db).toInt()
            return if (rounded < 0) "−${-rounded} dB" else "$rounded dB"
        }
    }
}
