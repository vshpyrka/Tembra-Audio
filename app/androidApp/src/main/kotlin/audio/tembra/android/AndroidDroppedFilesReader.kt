package audio.tembra.android

import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.view.DragAndDropPermissions
import android.view.DragEvent
import audio.tembra.ui.drop.AndroidDragReader
import audio.tembra.ui.drop.DroppedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files dragged onto the app on Android and ChromeOS (feature 011, spec FR-026; research R12): audio types, ChromeOS
 * Files' URI list, folders and the generic type some file managers report are accepted. On drop, while the event is
 * valid, the activity requests the drag's permissions and the clip's URIs are taken; they are read off the main thread.
 * The importer copies the files after the drop returns, so the permissions are held until the next drop or until the
 * activity ends ([detach]). A dragged folder is one directory URI whose children the app cannot read.
 */
class AndroidDroppedFilesReader(private val context: Context) : AndroidDragReader {
    private var activity: Activity? = null
    private var held: DragAndDropPermissions? = null

    fun attach(activity: Activity) {
        this.activity = activity
    }

    fun detach(activity: Activity) {
        if (this.activity !== activity) return
        held?.release()
        held = null
        this.activity = null
    }

    override fun acceptsTypes(types: Set<String>): Boolean = Companion.acceptsTypes(types)

    override suspend fun read(drag: DragEvent): List<DroppedItem> {
        val clip = drag.clipData ?: return emptyList()
        held?.release()
        held = activity?.requestDragAndDropPermissions(drag)
        val uris = urisOf(clip)
        val resolver = context.contentResolver
        return withContext(Dispatchers.IO) { uris.map { uri -> itemOf(uri, resolver.getType(uri), resolver) } }
    }

    internal companion object {
        /** ChromeOS Files' drag of files: the clip's text lists their URIs. */
        private const val ARC_URI_LIST = "application/x-arc-uri-list"
        private const val OCTET_STREAM = "application/octet-stream"

        internal fun acceptsTypes(types: Set<String>): Boolean = types.any { type ->
            type.startsWith("audio/") || type == ARC_URI_LIST || type == DocumentsContract.Document.MIME_TYPE_DIR || type == OCTET_STREAM
        }

        /** Each item's URI, or the URIs a ChromeOS URI list carries in its text, in clip order. */
        internal fun urisOf(clip: ClipData): List<Uri> = (0 until clip.itemCount).flatMap { index ->
            val item = clip.getItemAt(index)
            item.uri?.let(::listOf) ?: item.text?.lines().orEmpty().map { it.trim() }.filter { it.startsWith("content://") }.map(Uri::parse)
        }

        /**
         * A folder (its children unreadable), an audio file named as the picker names one, or something else by name. A
         * missing or generic type counts as audio: the importer reads the file and names what it refuses.
         */
        internal fun itemOf(uri: Uri, type: String?, resolver: ContentResolver): DroppedItem = when {
            type == DocumentsContract.Document.MIME_TYPE_DIR -> DroppedItem.Folder(AndroidAudioFilePicker.displayName(uri, resolver), contents = null)
            type == null || type.startsWith("audio/") || type == OCTET_STREAM ->
                DroppedItem.AudioFile(AndroidAudioFilePicker.pickedFile(uri, resolver, withExtension(AndroidAudioFilePicker.displayName(uri, resolver), type)))
            else -> DroppedItem.Other(AndroidAudioFilePicker.displayName(uri, resolver))
        }

        /**
         * [name] with its [type]'s extension when it has none: the importer decides the format by extension, and some
         * providers name a file without one (MediaStore's URIs end in the row's id).
         */
        private fun withExtension(name: String, type: String?): String {
            if ('.' in name) return name
            val extension = AUDIO_EXTENSIONS[type] ?: return name
            return "$name.$extension"
        }

        private val AUDIO_EXTENSIONS = mapOf(
            "audio/wav" to "wav",
            "audio/x-wav" to "wav",
            "audio/wave" to "wav",
            "audio/vnd.wave" to "wav",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
            "audio/mpeg" to "mp3",
            "audio/mp3" to "mp3",
        )
    }
}
