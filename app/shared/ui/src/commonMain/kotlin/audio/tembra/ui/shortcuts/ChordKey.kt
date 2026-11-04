package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key

/**
 * The key of a [KeyChord] (feature 011, research R6): a letter, digit or symbol is the [Character] it types, lower
 * case, so a chord follows the character on the key and not its position (FR-023); keys that type nothing (Space,
 * Escape, the arrows, Home, Delete, Backspace) are a [Named] key.
 */
sealed interface ChordKey {
    data class Character(val char: Char) : ChordKey

    data class Named(val key: Key) : ChordKey
}
