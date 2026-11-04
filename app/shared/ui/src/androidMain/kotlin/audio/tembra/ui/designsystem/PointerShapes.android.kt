package audio.tembra.ui.designsystem

import androidx.compose.ui.input.pointer.PointerIcon

/** The system's grab, grabbing and wait shapes (feature 011, research R7, R19). */
actual object PointerShapes {
    actual val grab: PointerIcon = PointerIcon(android.view.PointerIcon.TYPE_GRAB)
    actual val grabbing: PointerIcon = PointerIcon(android.view.PointerIcon.TYPE_GRABBING)
    actual val busy: PointerIcon = PointerIcon(android.view.PointerIcon.TYPE_WAIT)
}
