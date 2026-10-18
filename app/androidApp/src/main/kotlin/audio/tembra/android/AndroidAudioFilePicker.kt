package audio.tembra.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import audio.tembra.data.projects.PickedFile
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.AudioPickRequest
import audio.tembra.ui.platform.AudioPickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import okio.Source
import okio.source

/**
 * The system document picker for audio files (contracts/project-storage.md, research R11): `OpenDocument` or
 * `OpenMultipleDocuments` through [AudioFilePickerHost]; each picked document becomes a [PickedFile] whose opener
 * streams it through the content resolver while the Storage Access Framework grant lasts (the importer copies it at
 * once). Names come from `OpenableColumns.DISPLAY_NAME`, falling back to the URI's last segment.
 */
class AndroidAudioFilePicker(private val host: AudioFilePickerHost, private val context: Context) : AudioFilePicker {

    override suspend fun pickOne(request: AudioPickRequest): AudioPickResult = pick(request, multiple = false)

    override suspend fun pickMany(request: AudioPickRequest): AudioPickResult = pick(request, multiple = true)

    private suspend fun pick(request: AudioPickRequest, multiple: Boolean): AudioPickResult {
        val uris = host.open(mimeTypesOf(request), multiple) ?: return AudioPickResult.Cancelled
        val files = withContext(Dispatchers.IO) { uris.map { uri -> pickedFile(uri, context.contentResolver) } }
        return AudioPickResult.Picked(files)
    }

    internal companion object {
        /** The generic type some file managers report for every audio file, added to every request. */
        private const val OCTET_STREAM = "application/octet-stream"

        internal fun mimeTypesOf(request: AudioPickRequest): Array<String> = (request.mimeTypes + OCTET_STREAM).toTypedArray()

        /** The [PickedFile] for [uri]: its display name and an opener over the resolver's stream. */
        internal fun pickedFile(uri: Uri, resolver: ContentResolver): PickedFile =
            PickedFile(displayName(uri, resolver)) { open(uri, resolver) }

        private fun open(uri: Uri, resolver: ContentResolver): Source {
            val stream = try {
                resolver.openInputStream(uri)
            } catch (failure: SecurityException) {
                throw IOException("the document's grant was revoked", failure)
            } catch (failure: IllegalArgumentException) {
                throw IOException("the document could not be opened", failure)
            }
            return (stream ?: throw IOException("no stream for $uri")).source()
        }

        /**
         * `OpenableColumns.DISPLAY_NAME` with its extension; the URI's last path segment when the provider has no name,
         * a blank one, or refuses the query (some throw `SecurityException`, `IllegalArgumentException` or
         * `UnsupportedOperationException`); [FALLBACK_NAME] when neither gives a name.
         */
        internal fun displayName(uri: Uri, resolver: ContentResolver): String {
            val provided = try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
            return listOfNotNull(provided, uri.lastPathSegment).firstOrNull { it.isNotBlank() } ?: FALLBACK_NAME
        }

        /** The label of a picked file whose provider gives no usable name. */
        internal const val FALLBACK_NAME = "Audio file"
    }
}
