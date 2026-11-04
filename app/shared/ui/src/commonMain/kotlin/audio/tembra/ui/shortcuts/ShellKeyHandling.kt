package audio.tembra.ui.shortcuts

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * The shell's chords on its root (feature 011, contracts/keyboard-shortcuts.md rules 1, 5, 6): the primary modifier with
 * 1 to 5 opens the destinations in navigation order, with / the shortcut sheet. In the preview pass, so they work from
 * any focused control, except while a text field has focus ([textInput]); where a menu serves the primary chords
 * ([ShortcutPlatform.menuServesPrimaryChords], the iPad) they are left to it. Escape is the platform's (pop-ups, sheets
 * and dialogs close on it; Android's Escape-as-back otherwise).
 */
fun Modifier.shellKeyHandling(shell: ShellShortcuts, textInput: TextInputFocus, platform: ShortcutPlatform): Modifier =
    onPreviewKeyEvent { event ->
        if (textInput.focused) return@onPreviewKeyEvent false
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Everywhere, platform)
            ?: return@onPreviewKeyEvent false
        ShortcutDispatcher.dispatch(action, ShortcutTargets(shell = shell))
    }
