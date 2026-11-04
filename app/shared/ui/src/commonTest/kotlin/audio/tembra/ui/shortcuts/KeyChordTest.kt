package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one matching rule (feature 011, spec FR-023; research R6): letters and symbols follow the character the key
 * types (an AZERTY "A" whose physical key is Q matches `a`), named keys their key; the primary modifier is Ctrl on
 * Android and ChromeOS and Command on iPad; Alt and the other platform's modifier never match; key-down only.
 */
class KeyChordTest {

    private fun input(key: Key, codePoint: Int = 0, ctrl: Boolean = false, meta: Boolean = false, shift: Boolean = false, alt: Boolean = false, down: Boolean = true) =
        KeyInput(key = key, codePoint = codePoint, ctrl = ctrl, meta = meta, shift = shift, alt = alt, down = down)

    @Test
    fun aCharacterMatchesByWhatTheKeyTypes() {
        val s = KeyChord(ChordKey.Character('s'))
        // iOS reports the physical position: on an AZERTY keyboard the key typing "s" may report another Key.
        assertTrue(s.matches(input(Key.Q, codePoint = 's'.code), PrimaryModifier.Ctrl))
        assertTrue(s.matches(input(Key.S, codePoint = 'S'.code, shift = false), PrimaryModifier.Ctrl), "upper case is the same letter")
        assertFalse(s.matches(input(Key.S, codePoint = 'z'.code), PrimaryModifier.Ctrl), "the key alone does not match another character")
    }

    @Test
    fun aCharacterFallsBackToTheKeyWhenAModifierHidesTheCharacter() {
        // Android reports no character for Ctrl+letter on some keyboards.
        val find = KeyChord(ChordKey.Character('f'), primary = true)
        assertTrue(find.matches(input(Key.F, codePoint = 0, ctrl = true), PrimaryModifier.Ctrl))
    }

    @Test
    fun thePrimaryModifierIsCtrlOnAndroidAndCommandOnIpad() {
        val one = KeyChord(ChordKey.Character('1'), primary = true)
        assertTrue(one.matches(input(Key.One, codePoint = '1'.code, ctrl = true), PrimaryModifier.Ctrl))
        assertFalse(one.matches(input(Key.One, codePoint = '1'.code, meta = true), PrimaryModifier.Ctrl))
        assertTrue(one.matches(input(Key.One, codePoint = '1'.code, meta = true), PrimaryModifier.Meta))
        assertFalse(one.matches(input(Key.One, codePoint = '1'.code, ctrl = true), PrimaryModifier.Meta))
        assertFalse(one.matches(input(Key.One, codePoint = '1'.code), PrimaryModifier.Ctrl), "a plain 1 is not the chord")
    }

    @Test
    fun altAndExtraModifiersNeverMatch() {
        val space = KeyChord(ChordKey.Named(Key.Spacebar))
        assertTrue(space.matches(input(Key.Spacebar, codePoint = ' '.code), PrimaryModifier.Ctrl))
        assertFalse(space.matches(input(Key.Spacebar, alt = true), PrimaryModifier.Ctrl))
        assertFalse(space.matches(input(Key.Spacebar, ctrl = true), PrimaryModifier.Ctrl))
        assertFalse(space.matches(input(Key.Spacebar, shift = true), PrimaryModifier.Ctrl))
        assertTrue(KeyChord(ChordKey.Named(Key.DirectionUp), shift = true).matches(input(Key.DirectionUp, shift = true), PrimaryModifier.Ctrl))
    }

    @Test
    fun aSymbolMatchesWhateverShiftTypedIt() {
        // "+" is Shift+= on a US keyboard, its own key on others and on the number pad: the character decides.
        val plus = KeyChord(ChordKey.Character('+'))
        assertTrue(plus.matches(input(Key.Equals, codePoint = '+'.code, shift = true), PrimaryModifier.Ctrl))
        assertTrue(plus.matches(input(Key.NumPadAdd, codePoint = '+'.code), PrimaryModifier.Ctrl))
        assertTrue(plus.matches(input(Key.Plus, codePoint = 0), PrimaryModifier.Ctrl), "no character reported: the plus key")
        assertTrue(KeyChord(ChordKey.Character('-')).matches(input(Key.NumPadSubtract, codePoint = '-'.code), PrimaryModifier.Ctrl))
        assertFalse(KeyChord(ChordKey.Character('=')).matches(input(Key.Equals, codePoint = '+'.code, shift = true), PrimaryModifier.Ctrl))
    }

    @Test
    fun onlyKeyDownMatches() {
        assertFalse(KeyChord(ChordKey.Named(Key.Spacebar)).matches(input(Key.Spacebar, down = false), PrimaryModifier.Ctrl))
    }
}
