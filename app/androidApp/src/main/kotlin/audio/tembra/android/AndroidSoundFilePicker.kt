package audio.tembra.android

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import audio.tembra.ui.platform.SoundFilePicker
import audio.tembra.ui.platform.SoundPickFailure
import audio.tembra.ui.platform.SoundPickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The system document picker for the custom click sound (contracts/click-sounds.md): `OpenDocument` through
 * [SoundFilePickerHost], then a copy of the chosen document to the destination with a 64 MB cap.
 */
class AndroidSoundFilePicker(private val host: SoundFilePickerHost, private val context: Context) : SoundFilePicker {

    override suspend fun pickWav(destinationPath: String): SoundPickResult {
        val uri = host.open(MIME_TYPES) ?: return SoundPickResult.Cancelled
        return withContext(Dispatchers.IO) { copy(uri, File(destinationPath), context.contentResolver) }
    }

    internal companion object {
        /** WAV under every MIME type providers use for it, plus the generic type some file managers report. */
        private val MIME_TYPES = arrayOf("audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave", "application/octet-stream")

        /** The largest custom sound accepted: 64 MB. */
        private const val MAX_BYTES = 64L * 1024 * 1024

        /**
         * Copies the document at [uri] to [destination], replacing what is there. Refuses a document over 64 MB, by
         * its declared length when the provider knows it and by the bytes read otherwise; a failed copy leaves nothing
         * at the destination. Returns the document's display name without its extension.
         */
        internal fun copy(uri: Uri, destination: File, resolver: ContentResolver): SoundPickResult {
            val failure = try {
                val descriptor = resolver.openAssetFileDescriptor(uri, "r")
                if (descriptor == null) SoundPickFailure.Unreadable else descriptor.use { copyCapped(it, destination) }
            } catch (_: IOException) {
                SoundPickFailure.Unreadable
            } catch (_: SecurityException) {
                SoundPickFailure.Unreadable
            } catch (_: IllegalArgumentException) {
                SoundPickFailure.Unreadable
            } catch (_: UnsupportedOperationException) {
                SoundPickFailure.Unreadable
            }
            if (failure != null) {
                destination.delete()
                return SoundPickResult.Failed(failure)
            }
            return SoundPickResult.Picked(displayName(uri, resolver))
        }

        /** Streams at most [MAX_BYTES] into [destination]; null when the whole document was copied. */
        private fun copyCapped(descriptor: AssetFileDescriptor, destination: File): SoundPickFailure? {
            if (descriptor.length > MAX_BYTES) return SoundPickFailure.TooLarge
            destination.parentFile?.mkdirs()
            descriptor.createInputStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return SoundPickFailure.TooLarge
                        output.write(buffer, 0, read)
                    }
                }
            }
            return null
        }

        /**
         * `OpenableColumns.DISPLAY_NAME` without its extension; the URI's last path segment when the provider has no
         * name, a blank one, or refuses the query (some throw `SecurityException`, `IllegalArgumentException` or
         * `UnsupportedOperationException`); [FALLBACK_NAME] when neither gives a name.
         */
        private fun displayName(uri: Uri, resolver: ContentResolver): String {
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
            return listOfNotNull(provided, uri.lastPathSegment)
                .map { it.substringBeforeLast('.') }
                .firstOrNull { it.isNotBlank() }
                ?: FALLBACK_NAME
        }

        /** The label of a picked sound whose provider gives no usable name. */
        private const val FALLBACK_NAME = "Custom sound"
    }
}
