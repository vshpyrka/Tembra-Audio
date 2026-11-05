package audio.tembra.ui.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The open overlays, newest on top (feature 011, contracts/keyboard-shortcuts.md rule 4): Escape's close-overlay action
 * closes the one opened last (a menu over a sheet before the sheet); with none open it does nothing, so the key goes on.
 */
class OverlayStackTest {

    @Test
    fun closingClosesTheNewestOverlay() {
        val closed = mutableListOf<String>()
        val stack = OverlayStack()
        val sheet = stack.push { closed += "sheet" }
        stack.push { closed += "menu" }
        assertTrue(stack.closeTop())
        assertEquals(listOf("menu"), closed)
        // The menu leaves the stack when it leaves composition; until then a second close asks it again.
        stack.remove(sheet)
        assertTrue(stack.closeTop())
        assertEquals(listOf("menu", "menu"), closed)
    }

    @Test
    fun withNothingOpenClosingDoesNothing() {
        val stack = OverlayStack()
        assertFalse(stack.closeTop())
        val entry = stack.push { }
        stack.remove(entry)
        assertFalse(stack.closeTop())
    }
}
