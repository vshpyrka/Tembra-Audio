package audio.tembra.ui.drop

import android.view.DragEvent
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/**
 * The Android app's side of a drop (feature 011, research R12), in platform types only: which dragged MIME types it
 * takes, and what a drop's [DragEvent] holds. [androidDroppedFilesReader] adapts it to Compose's drop events.
 */
interface AndroidDragReader {
    fun acceptsTypes(types: Set<String>): Boolean

    /** Called from the drop callback: the drag's permissions and clip are taken before the first suspension. */
    suspend fun read(drag: DragEvent): List<DroppedItem>
}

/** [reader] as the app's [DroppedFilesReader]; Android tells a drag's types before the drop, not its item count. */
fun androidDroppedFilesReader(reader: AndroidDragReader): DroppedFilesReader = object : DroppedFilesReader {
    override fun accepts(event: DragAndDropEvent): Boolean = reader.acceptsTypes(event.mimeTypes())

    override fun audioCount(event: DragAndDropEvent): Int? = null

    override suspend fun read(event: DragAndDropEvent): List<DroppedItem> = reader.read(event.toAndroidDragEvent())
}
