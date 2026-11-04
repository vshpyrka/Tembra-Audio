package audio.tembra.ui.designsystem

import androidx.compose.ui.input.pointer.PointerIcon

/** iPadOS adapts its pointer to controls itself; Compose's pointer icons are inert on iOS (feature 011, research R7). */
actual object PointerShapes {
    actual val grab: PointerIcon = PointerIcon.Default
    actual val grabbing: PointerIcon = PointerIcon.Default
    actual val busy: PointerIcon = PointerIcon.Default
}
