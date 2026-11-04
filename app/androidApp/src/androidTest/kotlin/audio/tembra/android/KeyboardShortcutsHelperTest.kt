package audio.tembra.android

import android.content.Intent
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.shortcuts.ChordKey
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's system shortcut list (feature 011, spec FR-022; research R6): what Meta+/ shows comes from
 * `MainActivity.onProvideKeyboardShortcuts`, built from the catalogue: one group per scope in the catalogue's order,
 * every chord of every entry with its label, Ctrl for the primary modifier and Shift where the chord has it.
 */
class KeyboardShortcutsHelperTest {

    @Test
    fun theSystemListHasOneGroupPerScopeWithEveryChord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            val groups = mutableListOf<KeyboardShortcutGroup>()
            instrumentation.runOnMainSync { activity.onProvideKeyboardShortcuts(groups, null, 0) }

            assertEquals(ShortcutScope.entries.size, groups.size)
            assertTrue("a group has no title", groups.all { !it.label.isNullOrBlank() })
            assertEquals(groups.size, groups.map { it.label.toString() }.toSet().size)
            for ((group, scope) in groups.zip(ShortcutScope.entries)) {
                val expected = ShortcutCatalogue.all.filter { it.scope == scope }.flatMap { shortcut -> shortcut.chords.map { shortcut.label to it } }
                val items = group.items
                assertEquals("${group.label}", expected.size, items.size)
                for ((item, entry) in items.zip(expected)) {
                    val (label, chord) = entry
                    assertEquals(label, item.label.toString())
                    assertEquals("$label: Ctrl", chord.primary, item.modifiers and KeyEvent.META_CTRL_ON != 0)
                    assertEquals("$label: Shift", chord.shift, item.modifiers and KeyEvent.META_SHIFT_ON != 0)
                    when (val key = chord.key) {
                        is ChordKey.Character -> assertEquals(label, key.char.uppercaseChar(), item.baseCharacter.uppercaseChar())
                        is ChordKey.Named -> assertTrue("$label has no key code", item.keycode != KeyEvent.KEYCODE_UNKNOWN)
                    }
                }
            }
        } finally {
            activity.finish()
        }
    }
}
