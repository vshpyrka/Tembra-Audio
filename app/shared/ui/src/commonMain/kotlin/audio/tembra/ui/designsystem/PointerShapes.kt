package audio.tembra.ui.designsystem

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * The pointer shapes common code has no constant for (feature 011, spec FR-015, FR-015a; research R7, R19): an open
 * hand over a draggable clip, a closed one while dragging, and the system's busy shape while the app works. Android and
 * ChromeOS show the system's shapes; on iPad the system pointer adapts to controls by itself (Compose's pointer icons
 * are inert there), so these are the default.
 */
expect object PointerShapes {
    val grab: PointerIcon
    val grabbing: PointerIcon
    val busy: PointerIcon
}
