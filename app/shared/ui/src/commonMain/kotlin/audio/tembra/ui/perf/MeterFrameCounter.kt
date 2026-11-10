package audio.tembra.ui.perf

/**
 * The meter frames the UI missed (feature 012, contracts/perf-instrumentation.md `audio.meterFramesDropped`): the sheet's
 * frame loop reports each drawn frame's time; a frame later than twice the display period, the shortest interval seen
 * since the loop started, counts one dropped frame. Published once a second by `AudioHealthTicker` and written into
 * `engine-health.json`. One shared instance per process, as the health counters are; [pause] ends a run (the sheet
 * closed) so the next frame starts afresh without counting the gap. Main thread only.
 */
class MeterFrameCounter {
    var dropped: Long = 0L
        private set
    private var lastNanos = 0L
    private var periodNanos = 0L

    fun frame(frameNanos: Long) {
        if (lastNanos != 0L) {
            val delta = frameNanos - lastNanos
            if (delta > 0L) {
                if (periodNanos == 0L || delta < periodNanos) {
                    periodNanos = delta.coerceAtLeast(MIN_PERIOD_NANOS)
                } else if (delta > 2 * periodNanos) {
                    dropped++
                }
            }
        }
        lastNanos = frameNanos
    }

    fun pause() {
        lastNanos = 0L
    }

    fun reset() {
        dropped = 0L
        lastNanos = 0L
        periodNanos = 0L
    }

    companion object {
        /** The process-wide counter the sheet feeds and the health ticker publishes. */
        val shared = MeterFrameCounter()

        /** No display refreshes faster than 240 Hz: a shorter interval is two draws of one frame, not a period. */
        const val MIN_PERIOD_NANOS = 4_000_000L
    }
}
