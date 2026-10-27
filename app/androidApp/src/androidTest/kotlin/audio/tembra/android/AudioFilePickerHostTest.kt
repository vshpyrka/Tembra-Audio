package audio.tembra.android

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.platform.AudioPickRequest
import audio.tembra.ui.platform.AudioPickResult
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.buffer
import okio.use
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Android document picker's activity host and the content handles it hands over (contracts/project-storage.md,
 * T044): a fake launcher stands in for the activity result launchers.
 */
class AudioFilePickerHostTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "picker-test-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun removeTheFiles() {
        root.deleteRecursively()
    }

    private fun kilobyteFile(name: String): File = File(root, name).apply { writeBytes(ByteArray(1024) { it.toByte() }) }

    private class FakeLaunchers {
        val one = mutableListOf<List<String>>()
        val many = mutableListOf<List<String>>()
        val host = AudioFilePickerHost(launchOne = { one += it.toList() }, launchMany = { many += it.toList() })
    }

    private suspend fun awaitLaunch(count: () -> Int) = withTimeout(2_000) {
        while (count() == 0) yield()
    }

    @Test
    fun pickOneResolvesOnTheSingleResultWithTheFilesBytesAndName() = runBlocking {
        val launchers = FakeLaunchers()
        val picker = AndroidAudioFilePicker(launchers.host, context)
        val pending = async { picker.pickOne(AudioPickRequest.wav) }
        awaitLaunch { launchers.one.size }
        assertEquals(AudioPickRequest.wav.mimeTypes + "application/octet-stream", launchers.one.single())
        assertTrue(launchers.many.isEmpty())
        assertTrue(pending.isActive)
        val source = kilobyteFile("Rim.wav")
        launchers.host.onResult(Uri.fromFile(source))
        val result = withTimeout(2_000) { pending.await() } as AudioPickResult.Picked
        val file = result.files.single()
        assertEquals("Rim.wav", file.displayName)
        assertArrayEquals(source.readBytes(), file.open().buffer().use { it.readByteArray() })
    }

    @Test
    fun pickManyResolvesOnTheListInPickerOrder() = runBlocking {
        val launchers = FakeLaunchers()
        val picker = AndroidAudioFilePicker(launchers.host, context)
        val pending = async { picker.pickMany(AudioPickRequest.timelineAudio) }
        awaitLaunch { launchers.many.size }
        assertEquals(AudioPickRequest.timelineAudio.mimeTypes + "application/octet-stream", launchers.many.single())
        launchers.host.onResult(listOf(Uri.fromFile(kilobyteFile("b.wav")), Uri.fromFile(kilobyteFile("a.flac"))))
        val result = withTimeout(2_000) { pending.await() } as AudioPickResult.Picked
        assertEquals(listOf("b.wav", "a.flac"), result.files.map { it.displayName })
    }

    @Test
    fun aNullOrEmptyResultIsCancelled() = runBlocking {
        val launchers = FakeLaunchers()
        val picker = AndroidAudioFilePicker(launchers.host, context)
        val one = async { picker.pickOne(AudioPickRequest.wav) }
        awaitLaunch { launchers.one.size }
        launchers.host.onResult(null)
        assertEquals(AudioPickResult.Cancelled, withTimeout(2_000) { one.await() })
        val many = async { picker.pickMany(AudioPickRequest.wav) }
        awaitLaunch { launchers.many.size }
        launchers.host.onResult(emptyList())
        assertEquals(AudioPickResult.Cancelled, withTimeout(2_000) { many.await() })
    }

    @Test
    fun aSecondPickWhileOneIsPendingIsCancelled() = runBlocking {
        val launchers = FakeLaunchers()
        val picker = AndroidAudioFilePicker(launchers.host, context)
        val first = async { picker.pickOne(AudioPickRequest.wav) }
        awaitLaunch { launchers.one.size }
        assertEquals(AudioPickResult.Cancelled, withTimeout(2_000) { picker.pickOne(AudioPickRequest.wav) })
        assertEquals("the picker was opened again", 1, launchers.one.size)
        assertTrue(first.isActive)
        launchers.host.onResult(Uri.fromFile(kilobyteFile("Rim.wav")))
        assertEquals("Rim.wav", (withTimeout(2_000) { first.await() } as AudioPickResult.Picked).files.single().displayName)
    }

    private fun providerUri(path: String): Uri = Uri.parse("content://${TestSoundProvider.AUTHORITY}/$path")

    @Test
    fun aContentDocumentIsNamedByItsDisplayNameWithItsExtension() {
        val file = AndroidAudioFilePicker.pickedFile(providerUri("named/42"), context.contentResolver)
        assertEquals("Rim Shot.wav", file.displayName)
        assertArrayEquals(TestSoundProvider.BYTES, runBlocking { file.open().buffer().use { it.readByteArray() } })
    }

    @Test
    fun aBlankDisplayNameFallsBackToThePathSegment() {
        assertEquals("Cowbell.wav", AndroidAudioFilePicker.displayName(providerUri("blank/Cowbell.wav"), context.contentResolver))
    }

    @Test
    fun aFailingNameQueryFallsBackToThePathSegment() {
        val file = AndroidAudioFilePicker.pickedFile(providerUri("throwing/Clave.wav"), context.contentResolver)
        assertEquals("Clave.wav", file.displayName)
        assertArrayEquals(TestSoundProvider.BYTES, runBlocking { file.open().buffer().use { it.readByteArray() } })
    }
}
