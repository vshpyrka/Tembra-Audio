package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Master volume as a whole percentage (spec FR-007). */
class PercentTest {

    @Test
    fun rangeIsZeroToOneHundred() {
        Percent(0)
        Percent(100)
        assertFailsWith<IllegalArgumentException> { Percent(101) }
        assertFailsWith<IllegalArgumentException> { Percent(-1) }
    }

    @Test
    fun linearAndText() {
        assertEquals(0.8f, Percent(80).toLinear())
        assertEquals("80 %", Percent(80).readout)
        assertEquals(Percent(100), Percent.clamped(140))
    }
}
