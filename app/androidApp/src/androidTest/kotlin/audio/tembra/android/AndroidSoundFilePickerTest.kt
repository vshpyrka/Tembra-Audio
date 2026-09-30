package audio.tembra.android

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.platform.SoundPickFailure
import audio.tembra.ui.platform.SoundPickResult
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/** The Android document picker's copy step and its activity host (contracts/click-sounds.md). */
class AndroidSoundFilePickerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "picker-test-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun removeTheFiles() {
        root.deleteRecursively()
    }

    private fun kilobyteFile(name: String): File = File(root, name).apply { writeBytes(ByteArray(1024) { it.toByte() }) }

    @Test
    fun aKilobyteFileIsCopiedAndNamedWithoutItsExtension() {
        val source = kilobyteFile("Wood Block.wav")
        val destination = File(root, "staging/custom.wav")

        val result = AndroidSoundFilePicker.copy(Uri.fromFile(source), destination, context.contentResolver)

        assertEquals(SoundPickResult.Picked("Wood Block"), result)
        assertArrayEquals(source.readBytes(), destination.readBytes())
    }

    @Test
    fun aFileOverSixtyFourMegabytesIsRefused() {
        val source = File(root, "huge.wav")
        RandomAccessFile(source, "rw").use { it.setLength(65L * 1024 * 1024) }
        val destination = File(root, "custom.wav")

        val result = AndroidSoundFilePicker.copy(Uri.fromFile(source), destination, context.contentResolver)

        assertEquals(SoundPickResult.Failed(SoundPickFailure.TooLarge), result)
        assertFalse(destination.exists())
    }

    private fun providerUri(path: String): Uri = Uri.parse("content://${TestSoundProvider.AUTHORITY}/$path")

    @Test
    fun aContentDocumentIsNamedByItsDisplayName() {
        val destination = File(root, "custom.wav")

        val result = AndroidSoundFilePicker.copy(providerUri("named/42"), destination, context.contentResolver)

        assertEquals(SoundPickResult.Picked("Rim Shot"), result)
        assertArrayEquals(TestSoundProvider.BYTES, destination.readBytes())
    }

    @Test
    fun aBlankDisplayNameFallsBackToThePathSegment() {
        val result = AndroidSoundFilePicker.copy(providerUri("blank/Cowbell.wav"), File(root, "custom.wav"), context.contentResolver)

        assertEquals(SoundPickResult.Picked("Cowbell"), result)
    }

    @Test
    fun aFailingNameQueryFallsBackToThePathSegment() {
        val destination = File(root, "custom.wav")

        val result = AndroidSoundFilePicker.copy(providerUri("throwing/Clave.wav"), destination, context.contentResolver)

        assertEquals(SoundPickResult.Picked("Clave"), result)
        assertArrayEquals(TestSoundProvider.BYTES, destination.readBytes())
    }

    @Test
    fun pickWavSuspendsUntilTheLauncherResultArrives() = runBlocking {
        var launched: List<String>? = null
        val host = SoundFilePickerHost { launched = it.toList() }
        val picker = AndroidSoundFilePicker(host, context)
        val destination = File(root, "custom.wav")
        val pending = async { picker.pickWav(destination.path) }
        withTimeout(2_000) {
            while (launched == null) yield()
        }
        assertEquals(listOf("audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave", "application/octet-stream"), launched)
        assertTrue(pending.isActive)
        host.onResult(Uri.fromFile(kilobyteFile("Rim.wav")))
        assertEquals(SoundPickResult.Picked("Rim"), withTimeout(2_000) { pending.await() })
        assertEquals(1024L, destination.length())
    }

    @Test
    fun aNullResultIsCancelled() = runBlocking {
        var launches = 0
        val host = SoundFilePickerHost { launches++ }
        val picker = AndroidSoundFilePicker(host, context)
        val pending = async { picker.pickWav(File(root, "custom.wav").path) }
        withTimeout(2_000) {
            while (launches == 0) yield()
        }
        host.onResult(null)
        assertEquals(SoundPickResult.Cancelled, withTimeout(2_000) { pending.await() })
    }

    @Test
    fun aSecondPickWhileOneIsPendingIsCancelled() = runBlocking {
        var launches = 0
        val host = SoundFilePickerHost { launches++ }
        val picker = AndroidSoundFilePicker(host, context)
        val first = async { picker.pickWav(File(root, "first.wav").path) }
        withTimeout(2_000) {
            while (launches == 0) yield()
        }
        assertEquals(SoundPickResult.Cancelled, withTimeout(2_000) { picker.pickWav(File(root, "second.wav").path) })
        assertEquals("the picker was opened again", 1, launches)
        assertTrue(first.isActive)
        host.onResult(Uri.fromFile(kilobyteFile("Rim.wav")))
        assertEquals(SoundPickResult.Picked("Rim"), withTimeout(2_000) { first.await() })
    }
}
