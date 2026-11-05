package audio.tembra.ui.drop

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset

/**
 * Where [this] drag is, in the root layout's pixels, or null where the platform's drop event does not say (iOS with
 * Compose Multiplatform 1.12 keeps the drop session and its location internal; research R12 follow-up).
 */
internal expect fun DragAndDropEvent.rootPosition(): Offset?
