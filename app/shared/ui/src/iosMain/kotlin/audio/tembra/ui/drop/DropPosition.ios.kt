package audio.tembra.ui.drop

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset

/** Compose Multiplatform 1.12 keeps the iOS drop session and its location internal: recheck with 1.13. */
internal actual fun DragAndDropEvent.rootPosition(): Offset? = null
