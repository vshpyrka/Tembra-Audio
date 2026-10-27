package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** The three count-in choices and their bar counts (feature 010, data-model.md section 1; spec FR-009). */
class CountInTest {

    @Test
    fun threeChoicesInTheSpecOrder() {
        assertEquals(listOf(CountIn.OFF, CountIn.ONE_BAR, CountIn.TWO_BARS), CountIn.entries)
    }

    @Test
    fun barsAreZeroOneAndTwo() {
        assertEquals(0, CountIn.OFF.bars)
        assertEquals(1, CountIn.ONE_BAR.bars)
        assertEquals(2, CountIn.TWO_BARS.bars)
    }
}
