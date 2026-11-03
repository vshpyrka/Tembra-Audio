package audio.tembra.ui.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The overlays open now, newest last (feature 011, contracts/keyboard-shortcuts.md rule 4): menus, pop-ups, sheets and
 * dialogs register their dismissal while open ([RegisterOverlay]), and the catalogue's close-overlay action closes the
 * newest ([closeTop]). On Android a focused pop-up or dialog takes Escape itself; on iPad UIKit keeps Escape from
 * Compose, so the menu bar's Escape command runs this action. Main thread only.
 */
class OverlayStack {
    private val entries = mutableListOf<Entry>()

    /** One open overlay; pass it back to [remove] when the overlay closes. */
    class Entry internal constructor(internal val dismiss: () -> Unit)

    fun push(dismiss: () -> Unit): Entry = Entry(dismiss).also { entries += it }

    fun remove(entry: Entry) {
        entries -= entry
    }

    /** Dismisses the newest overlay; false when none is open. */
    fun closeTop(): Boolean {
        val top = entries.lastOrNull() ?: return false
        top.dismiss()
        return true
    }
}

/** The shell's [OverlayStack]; none outside the shell (previews, most tests), where registering does nothing. */
val LocalOverlayStack = staticCompositionLocalOf<OverlayStack?> { null }

/** Registers an open overlay whose dismissal is [onDismiss], for as long as this composable is in composition. */
@Composable
internal fun RegisterOverlay(onDismiss: () -> Unit) {
    val stack = LocalOverlayStack.current ?: return
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(stack) {
        val entry = stack.push { dismiss() }
        onDispose { stack.remove(entry) }
    }
}
