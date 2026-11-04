package audio.tembra.android

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.drop.DroppedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a drag onto the app holds on Android and ChromeOS (feature 011, spec FR-026; research R12): audio types,
 * ChromeOS Files' URI list and a folder are accepted; each `content://` URI becomes an audio file named by its provider
 * (the picker's rule), a dragged folder arrives as a directory URI whose children the app cannot read, and anything else
 * is named so the message can list it. The drop's permissions themselves need a real drag (quickstart §5).
 */
class AndroidDroppedFilesReaderTest {

    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Test
    fun audioChromeOsUriListsAndFoldersAreAccepted() {
        assertTrue(AndroidDroppedFilesReader.acceptsTypes(setOf("audio/wav")))
        assertTrue(AndroidDroppedFilesReader.acceptsTypes(setOf("application/x-arc-uri-list")))
        assertTrue(AndroidDroppedFilesReader.acceptsTypes(setOf(DocumentsContract.Document.MIME_TYPE_DIR)))
        assertTrue(AndroidDroppedFilesReader.acceptsTypes(setOf("application/octet-stream")))
        assertFalse(AndroidDroppedFilesReader.acceptsTypes(setOf("text/plain")))
        assertFalse(AndroidDroppedFilesReader.acceptsTypes(setOf("image/png")))
    }

    @Test
    fun eachUriBecomesAnAudioFileAFolderOrANamedOtherItem() {
        val wav = Uri.parse("content://audio.tembra.test/stems/kick.wav")
        val folder = Uri.parse("content://audio.tembra.test/tree/Stems")
        val text = Uri.parse("content://audio.tembra.test/notes.txt")
        val audio = AndroidDroppedFilesReader.itemOf(wav, "audio/wav", resolver) as DroppedItem.AudioFile
        assertEquals("kick.wav", audio.file.displayName)
        val dir = AndroidDroppedFilesReader.itemOf(folder, DocumentsContract.Document.MIME_TYPE_DIR, resolver) as DroppedItem.Folder
        assertEquals("Stems", dir.name)
        assertNull("a dragged folder's children are not readable", dir.contents)
        assertEquals(DroppedItem.Other("notes.txt"), AndroidDroppedFilesReader.itemOf(text, "text/plain", resolver))
    }

    @Test
    fun aNameWithoutAnExtensionTakesItsTypes() {
        // The importer decides the format by extension; MediaStore's own URIs end in an id ("19"), seen on the emulator.
        val id = Uri.parse("content://audio.tembra.test/audio/media/19")
        assertEquals("19.wav", (AndroidDroppedFilesReader.itemOf(id, "audio/wav", resolver) as DroppedItem.AudioFile).file.displayName)
        assertEquals("19.wav", (AndroidDroppedFilesReader.itemOf(id, "audio/x-wav", resolver) as DroppedItem.AudioFile).file.displayName)
        assertEquals("19.flac", (AndroidDroppedFilesReader.itemOf(id, "audio/flac", resolver) as DroppedItem.AudioFile).file.displayName)
        assertEquals("19.mp3", (AndroidDroppedFilesReader.itemOf(id, "audio/mpeg", resolver) as DroppedItem.AudioFile).file.displayName)
        assertEquals("19", (AndroidDroppedFilesReader.itemOf(id, "application/octet-stream", resolver) as DroppedItem.AudioFile).file.displayName)
    }

    @Test
    fun aClipsUrisComeFromItsItemsOrChromeOsUriListText() {
        val clip = ClipData(ClipDescription("drop", arrayOf("audio/wav")), ClipData.Item(Uri.parse("content://a/1.wav")))
        clip.addItem(ClipData.Item(Uri.parse("content://a/2.wav")))
        assertEquals(listOf("content://a/1.wav", "content://a/2.wav"), AndroidDroppedFilesReader.urisOf(clip).map { it.toString() })
        val arc = ClipData(ClipDescription("drop", arrayOf("application/x-arc-uri-list")), ClipData.Item("content://b/1.wav\ncontent://b/2.flac\n"))
        assertEquals(listOf("content://b/1.wav", "content://b/2.flac"), AndroidDroppedFilesReader.urisOf(arc).map { it.toString() })
    }
}
