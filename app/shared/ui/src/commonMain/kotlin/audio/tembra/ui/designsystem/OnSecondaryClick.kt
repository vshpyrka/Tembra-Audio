package audio.tembra.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A secondary click (right button, or a two-finger trackpad click) on this element reports its position, local to the
 * element, to [onSecondaryClick] (feature 011, spec FR-012; contracts/pointer-input.md). The press is consumed in the
 * initial pass, before the element's own `clickable` sees it, so a right-click never also runs the primary action (on
 * Android it otherwise would). [key] restarts the handler, as `pointerInput`'s key does.
 */
internal fun Modifier.onSecondaryClick(key: Any?, onSecondaryClick: (Offset) -> Unit): Modifier = pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
            val change = event.changes.firstOrNull() ?: continue
            event.changes.forEach { it.consume() }
            onSecondaryClick(change.position)
        }
    }
}
