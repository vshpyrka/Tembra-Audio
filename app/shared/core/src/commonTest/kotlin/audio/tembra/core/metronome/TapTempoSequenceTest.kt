package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tap tempo arithmetic (spec FR-016). */
class TapTempoSequenceTest {

    private fun ms(value: Long): Long = value * 1_000_000L

    private fun sequenceAt(vararg atMs: Long): TapTempoSequence =
        atMs.fold(TapTempoSequence()) { sequence, t -> sequence.tap(ms(t)) }

    @Test
    fun oneTapGivesNoTempo() {
        val sequence = sequenceAt(0)
        assertNull(sequence.tempo)
        assertEquals(1, sequence.count)
    }

    @Test
    fun twoTapsHalfASecondApartGive120() {
        assertEquals(Tempo(120), sequenceAt(0, 500).tempo)
    }

    @Test
    fun eightJitteredTapsAverageToAbout100() {
        val jitter = listOf(0L, 15L, -20L, 10L, -5L, 20L, -15L, 5L)
        var sequence = TapTempoSequence()
        jitter.forEachIndexed { index, offset -> sequence = sequence.tap(ms(index * 600L + offset)) }
        val bpm = sequence.tempo!!.bpm
        assertTrue(bpm in 99..101, "bpm $bpm")
    }

    @Test
    fun aGapOverTwoSecondsStartsOver() {
        val sequence = sequenceAt(0, 500, 3_000)
        assertEquals(1, sequence.count)
        assertNull(sequence.tempo)
    }

    @Test
    fun resultIsClamped() {
        assertEquals(Tempo(240), sequenceAt(0, 200, 400, 600).tempo)
        assertEquals(Tempo(40), sequenceAt(0, 1_800, 3_600).tempo)
    }

    @Test
    fun onlyTheLastEightIntervalsCount() {
        // Four slow intervals (1000 ms) then eight at 500 ms: only the fast eight remain.
        val times = mutableListOf(0L)
        repeat(4) { times += times.last() + 1_000 }
        repeat(8) { times += times.last() + 500 }
        val sequence = sequenceAt(*times.toLongArray())
        assertEquals(TapTempoSequence.MAX_INTERVALS + 1, sequence.tapNanos.size)
        assertEquals(Tempo(120), sequence.tempo)
    }

    @Test
    fun twelveTapsKeepNineTimestamps() {
        val sequence = sequenceAt(*LongArray(12) { it * 500L })
        assertEquals(9, sequence.tapNanos.size)
        assertEquals(ms(3 * 500L), sequence.tapNanos.first())
    }

    @Test
    fun constants() {
        assertEquals(8, TapTempoSequence.MAX_INTERVALS)
        assertEquals(2_000_000_000L, TapTempoSequence.WINDOW_NANOS)
    }
}
