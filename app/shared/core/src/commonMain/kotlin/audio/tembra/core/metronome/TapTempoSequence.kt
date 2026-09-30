package audio.tembra.core.metronome

import kotlin.math.roundToInt

/** Timestamps of the current run of taps, most recent last; at most nine are kept (eight intervals). */
data class TapTempoSequence(val tapNanos: List<Long> = emptyList()) {

    /** This sequence after a tap at [atNanos]; a gap over [WINDOW_NANOS] starts a new sequence. */
    fun tap(atNanos: Long): TapTempoSequence {
        val last = tapNanos.lastOrNull()
        val kept = if (last == null || atNanos - last > WINDOW_NANOS) emptyList() else tapNanos
        return TapTempoSequence((kept + atNanos).takeLast(MAX_INTERVALS + 1))
    }

    /** The mean interval as a whole BPM held inside the range; null with fewer than two taps (spec FR-016). */
    val tempo: Tempo?
        get() {
            if (tapNanos.size < 2) return null
            val meanNanos = (tapNanos.last() - tapNanos.first()).toDouble() / (tapNanos.size - 1)
            if (meanNanos <= 0.0) return Tempo(Tempo.MAX)
            return Tempo.clamped((60_000_000_000.0 / meanNanos).roundToInt())
        }

    val count: Int get() = tapNanos.size

    companion object {
        const val WINDOW_NANOS = 2_000_000_000L
        const val MAX_INTERVALS = 8
    }
}
