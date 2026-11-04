package audio.tembra.ui.drop

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset

/** The drag event's position is in the Compose view's own pixels, which is the root layout's. */
internal actual fun DragAndDropEvent.rootPosition(): Offset? = toAndroidDragEvent().let { Offset(it.x, it.y) }
