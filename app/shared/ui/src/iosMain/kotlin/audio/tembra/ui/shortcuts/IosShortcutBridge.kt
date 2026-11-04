package audio.tembra.ui.shortcuts

/**
 * One command of the iPad's menu bar (feature 011, spec FR-022; contracts/keyboard-shortcuts.md rule 5): the [action]'s
 * name for [IosShortcutBridge.dispatch], its [label], and its key equivalent: [input] (one character), with Command
 * when [command] and Shift when [shift].
 */
class MenuCommand(val action: String, val label: String, val input: String, val command: Boolean, val shift: Boolean)

/** One menu of the menu bar: a scope's [title] and its [commands], in catalogue order. */
class MenuSection(val title: String, val commands: List<MenuCommand>)

/**
 * The catalogue's Command chords for SwiftUI's `.commands` (feature 011, research R6), and the way back in: a menu
 * command calls [dispatch] with its action, which [ShortcutDispatcher] routes to the targets showing now
 * ([ShortcutRegistry]). The Compose handlers leave these chords alone on iPad, so each fires once.
 */
object IosShortcutBridge {
    /** Every scope that has a Command chord, in navigation order; Undo and Redo are left to [undoRedo]. */
    val sections: List<MenuSection> = ShortcutScope.entries.mapNotNull { scope ->
        val commands = ShortcutCatalogue.all
            .filter { it.scope == scope && it.action !in UndoRedo }
            .mapNotNull { it.command() }
        if (commands.isEmpty()) null else MenuSection(ShortcutCopy.scopeTitle(scope), commands)
    }

    /** The Timeline's Undo and Redo, which take the place of the system's Edit menu items. */
    val undoRedo: List<MenuCommand> = UndoRedo.mapNotNull { action -> ShortcutCatalogue.all.first { it.action == action }.command() }

    /**
     * Runs [action] on the targets showing now; false when it names no action, nothing can take it, or a text field has
     * focus and the action is one of the shell's chords (rule 1) or an undo the field takes itself. Close-overlay acts
     * from a text field too.
     */
    fun dispatch(action: String): Boolean {
        val shortcut = ShortcutCatalogue.all.firstOrNull { it.action.name == action } ?: return false
        val waitsForTyping = (shortcut.scope == ShortcutScope.Everywhere && shortcut.action != ShortcutAction.CloseOverlay) || shortcut.action in UndoRedo
        if (ShortcutRegistry.typing && waitsForTyping) return false
        return ShortcutDispatcher.dispatch(shortcut.action, ShortcutRegistry.current)
    }

    /** The entry's menu command: its [Shortcut.menuChord] (close-overlay's is ⌘., as UIKit keeps Escape from the app). */
    private fun Shortcut.command(): MenuCommand? {
        val chord = menuChord ?: return null
        val input = when (val key = chord.key) {
            is ChordKey.Character -> key.char.toString()
            // No Command chord uses a named key; a menu item could not show one without a mapping.
            is ChordKey.Named -> return null
        }
        return MenuCommand(action = action.name, label = label, input = input, command = chord.primary, shift = chord.shift)
    }
}

private val UndoRedo = listOf(ShortcutAction.TimelineUndo, ShortcutAction.TimelineRedo)
