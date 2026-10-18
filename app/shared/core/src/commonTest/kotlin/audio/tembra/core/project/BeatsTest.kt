package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Beat arithmetic (Constitution V: everything musical in beats). */
class BeatsTest {

    @Test
    fun arithmeticAndComparison() {
        assertEquals(Beats(6.5), Beats(4.0) + Beats(2.5))
        assertEquals(Beats(1.5), Beats(4.0) - Beats(2.5))
        assertEquals(Beats(8.0), Beats(4.0) * 2.0)
        assertTrue(Beats(1.0) < Beats(1.5))
        assertEquals(Beats.Zero, Beats(-3.0).coerceAtLeastZero())
        assertEquals(Beats(3.0), Beats(3.0).coerceAtLeastZero())
    }
}
