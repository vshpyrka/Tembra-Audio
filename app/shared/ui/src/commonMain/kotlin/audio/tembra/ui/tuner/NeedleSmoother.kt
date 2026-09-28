package audio.tembra.ui.tuner

/** The drawn needle angle between frames: a critically damped approach with an 80 ms time constant (research R8). */
internal class NeedleSmoother {
    private var angle = 0f
    private var lastNanos = 0L

    fun advance(target: Float, frameNanos: Long): Float {
        val dtMillis = if (lastNanos == 0L) 0L else ((frameNanos - lastNanos) / 1_000_000L).coerceIn(0L, MAX_STEP_MILLIS)
        lastNanos = frameNanos
        angle = MeterGeometry.smoothAngle(angle, target, dtMillis)
        return angle
    }

    private companion object {
        /** A long pause (the app was hidden) is treated as one long frame, not a jump. */
        const val MAX_STEP_MILLIS = 100L
    }
}
