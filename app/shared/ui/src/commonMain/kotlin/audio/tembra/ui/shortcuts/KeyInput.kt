package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/** What [KeyChord.matches] reads from a key event, as plain values (a platform `KeyEvent` cannot be built in common tests). */
data class KeyInput(
    val key: Key,
    val codePoint: Int,
    val ctrl: Boolean,
    val meta: Boolean,
    val shift: Boolean,
    val alt: Boolean,
    val down: Boolean,
) {
    companion object {
        fun of(event: KeyEvent): KeyInput = KeyInput(
            key = event.key,
            codePoint = event.utf16CodePoint,
            ctrl = event.isCtrlPressed,
            meta = event.isMetaPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            down = event.type == KeyEventType.KeyDown,
        )
    }
}
