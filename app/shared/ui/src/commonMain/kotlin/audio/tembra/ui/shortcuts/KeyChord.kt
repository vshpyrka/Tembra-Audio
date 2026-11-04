package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

/**
 * One key with its modifiers (feature 011, research R6): [primary] is the platform's usual modifier (Ctrl, or Command
 * on iPad), [shift] Shift. There is deliberately no Alt or Meta flag: both platforms reserve those combinations.
 */
data class KeyChord(val key: ChordKey, val primary: Boolean = false, val shift: Boolean = false) {

    fun matches(event: KeyEvent, primaryModifier: PrimaryModifier): Boolean = matches(KeyInput.of(event), primaryModifier)

    /**
     * The single matching rule: key-down only; exactly the chord's modifiers (the platform's primary one for
     * [primary], Shift for [shift], never Alt or the other platform's modifier); a [ChordKey.Character] by the
     * character typed, lower-cased, or by its key when a modifier leaves no character (a symbol whatever Shift it
     * took to type it); a [ChordKey.Named] by its key.
     */
    fun matches(input: KeyInput, primaryModifier: PrimaryModifier): Boolean {
        if (!input.down || input.alt) return false
        val primaryPressed = if (primaryModifier == PrimaryModifier.Ctrl) input.ctrl else input.meta
        val otherPressed = if (primaryModifier == PrimaryModifier.Ctrl) input.meta else input.ctrl
        if (primaryPressed != primary || otherPressed) return false
        return when (key) {
            is ChordKey.Named -> input.key == key.key && input.shift == shift
            is ChordKey.Character -> characterMatches(key.char, input) && (shift == input.shift || !key.char.isLetterOrDigit())
        }
    }

    private fun characterMatches(char: Char, input: KeyInput): Boolean = when {
        input.codePoint > 0 -> input.codePoint.toChar().lowercaseChar() == char
        // Some keyboards report no character while Ctrl is held: fall back to the letter's or digit's key.
        else -> input.key == keyOf(char)
    }

    private fun keyOf(char: Char): Key? = when (char) {
        in 'a'..'z' -> LETTERS[char - 'a']
        in '0'..'9' -> DIGITS[char - '0']
        '/' -> Key.Slash
        '.' -> Key.Period
        '+' -> Key.Plus
        '=' -> Key.Equals
        '-' -> Key.Minus
        else -> null
    }

    private companion object {
        val LETTERS = listOf(
            Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
            Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
        )
        val DIGITS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    }
}
