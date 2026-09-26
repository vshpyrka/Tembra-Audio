package audio.tembra.core.tuner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** String numerals I to VII (spec FR-017). */
class RomanNumeralsTest {

    @Test
    fun oneToSeven() {
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI", "VII"), (1..7).map { RomanNumerals.of(it) })
    }

    @Test
    fun outOfRangeThrows() {
        assertFailsWith<IllegalArgumentException> { RomanNumerals.of(0) }
        assertFailsWith<IllegalArgumentException> { RomanNumerals.of(8) }
    }
}
