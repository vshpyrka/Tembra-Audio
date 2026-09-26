package audio.tembra.core.tuner

/** String numerals for the By Ear cards: `I` is the highest string (spec FR-017). */
object RomanNumerals {
    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    fun of(value: Int): String {
        require(value in 1..NUMERALS.size) { "No string numeral for $value (1..${NUMERALS.size})" }
        return NUMERALS[value - 1]
    }
}
