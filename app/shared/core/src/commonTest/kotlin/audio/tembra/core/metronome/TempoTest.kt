package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tempo range, clamping and stepping (contracts/metronome-domain.md, spec FR-002). */
class TempoTest {

    @Test
    fun holdsWholeBpm() {
        assertEquals(120, Tempo(120).bpm)
    }

    @Test
    fun refusesValuesOutsideTheRange() {
        assertFailsWith<IllegalArgumentException> { Tempo(39) }
        assertFailsWith<IllegalArgumentException> { Tempo(241) }
    }

    @Test
    fun clampedNeverThrows() {
        assertEquals(Tempo(240), Tempo.clamped(300))
        assertEquals(Tempo(40), Tempo.clamped(0))
        assertEquals(Tempo(100), Tempo.clamped(100))
    }

    @Test
    fun plusClampsAtBothEnds() {
        assertEquals(Tempo(240), Tempo(240).plus(1))
        assertEquals(Tempo(40), Tempo(40).plus(-10))
        assertEquals(Tempo(121), Tempo(120).plus(1))
    }

    @Test
    fun constantsMatchTheSpec() {
        assertEquals(Tempo(120), Tempo.Default)
        assertEquals(40, Tempo.MIN)
        assertEquals(240, Tempo.MAX)
    }
}
