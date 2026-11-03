package audio.tembra.ui.shortcuts

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.ui.input.key.nativeKeyCode

/**
 * The catalogue as Android's system shortcut list (feature 011, spec FR-022; research R6), which Meta+/ shows: one group
 * per scope, titled as on the shortcut sheet, each chord of each entry with its label; Ctrl stands for the primary
 * modifier. Returned to `Activity.onProvideKeyboardShortcuts`.
 */
fun systemShortcutGroups(): List<KeyboardShortcutGroup> = ShortcutScope.entries.map { scope ->
    val items = ShortcutCatalogue.all.filter { it.scope == scope }.flatMap { shortcut ->
        shortcut.chords.map { chord -> chord.info(shortcut.label) }
    }
    KeyboardShortcutGroup(ShortcutCopy.scopeTitle(scope), items)
}

private fun KeyChord.info(label: String): KeyboardShortcutInfo {
    val modifiers = (if (primary) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0)
    return when (val key = key) {
        is ChordKey.Character -> KeyboardShortcutInfo(label, key.char, modifiers)
        is ChordKey.Named -> KeyboardShortcutInfo(label, key.key.nativeKeyCode, modifiers)
    }
}
