package audio.tembra.ui.drop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draganddrop.DragAndDropEvent

/**
 * Reads what was dragged onto the app (feature 011, spec FR-026; research R12), bound per platform and provided at the
 * app's root ([LocalDroppedFilesReader]): Android from the drag's `content://` URIs under the drop's permissions, iOS
 * from the drop session's item providers.
 */
interface DroppedFilesReader {
    /** Whether the drag may hold audio: its types only, before the drop. */
    fun accepts(event: DragAndDropEvent): Boolean

    /** How many audio items the drag holds, where the platform says before the drop; null when it does not (Android). */
    fun audioCount(event: DragAndDropEvent): Int?

    /**
     * The dropped items. Called from the drop callback; whatever must happen while the drop event is valid (Android's
     * drag permissions, iOS's item providers) happens before the first suspension.
     */
    suspend fun read(event: DragAndDropEvent): List<DroppedItem>
}

/** A platform or test without drops: no drag is accepted. */
object NoDroppedFiles : DroppedFilesReader {
    override fun accepts(event: DragAndDropEvent): Boolean = false

    override fun audioCount(event: DragAndDropEvent): Int? = null

    override suspend fun read(event: DragAndDropEvent): List<DroppedItem> = emptyList()
}

/** The platform's reader; [NoDroppedFiles] outside the app (previews, tests), where no zone takes drops. */
val LocalDroppedFilesReader = staticCompositionLocalOf<DroppedFilesReader> { NoDroppedFiles }
