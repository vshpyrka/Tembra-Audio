package audio.tembra.ui.navigation

import audio.tembra.ui.shortcuts.OverlayStack
import audio.tembra.ui.shortcuts.ShellShortcuts

/**
 * The shell's shortcut actions (feature 011, spec FR-017, FR-022): [open] selects a destination as its navigation item
 * does (re-selecting the shown one changes nothing), [showShortcuts] opens the shortcut sheet, and close-overlay closes
 * the newest of [overlays]. Stable for the shell's lifetime, so the iPad menu's registry keeps one instance.
 */
internal class ShellShortcutTargets(
    private val open: (Destination) -> Unit,
    private val showShortcuts: () -> Unit,
    private val overlays: OverlayStack,
) : ShellShortcuts {
    override fun open(destination: Destination): Boolean = true.also { open.invoke(destination) }

    override fun showShortcuts(): Boolean = true.also { showShortcuts.invoke() }

    override fun closeOverlay(): Boolean = overlays.closeTop()
}
