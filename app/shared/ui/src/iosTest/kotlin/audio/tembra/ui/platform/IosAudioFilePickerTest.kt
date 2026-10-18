package audio.tembra.ui.platform

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UniformTypeIdentifiers.UTType
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The iOS picker with an injected presentation (contracts/project-storage.md, T044): one or several files in order,
 * cancellation, and the security-scoped access released once the copy's source is closed. The presented sheet itself
 * is exercised by hand.
 */
class IosAudioFilePickerTest {

    private val root = NSTemporaryDirectory().trimEnd('/') + "/picker-test-${Random.nextLong()}"
    private val fileSystem = FileSystem.SYSTEM

    /** Records every begin and end so a test can prove the access is released, and when. */
    private class RecordingAccess : SecurityScopedAccess {
        val log = mutableListOf<String>()
        override fun begin(url: NSURL): Boolean {
            log += "begin ${url.lastPathComponent}"
            return true
        }
        override fun end(url: NSURL) {
            log += "end ${url.lastPathComponent}"
        }
    }

    init {
        fileSystem.createDirectories(root.toPath())
    }

    @AfterTest
    fun removeTheFiles() {
        fileSystem.deleteRecursively(root.toPath())
    }

    private fun file(name: String, bytes: ByteArray): NSURL {
        val path = "$root/$name"
        fileSystem.write(path.toPath()) { write(bytes) }
        return NSURL.fileURLWithPath(path)
    }

    private fun picker(answer: List<NSURL>?, access: SecurityScopedAccess = RecordingAccess(), onPresent: (List<UTType>, Boolean) -> Unit = { _, _ -> }) =
        IosAudioFilePicker(present = { types, multiple -> onPresent(types, multiple); answer }, access = access, fileSystem = fileSystem)

    @Test
    fun pickOneReturnsOneFileWhoseOpenerYieldsTheBytes() = runTest {
        val bytes = ByteArray(1024) { it.toByte() }
        val url = file("Wood Block.wav", bytes)
        var presented: Pair<List<UTType>, Boolean>? = null
        val result = picker(listOf(url), onPresent = { types, multiple -> presented = types to multiple }).pickOne(AudioPickRequest.wav)
        val picked = assertIs<AudioPickResult.Picked>(result)
        assertEquals(1, picked.files.size)
        assertEquals("Wood Block.wav", picked.files.single().displayName)
        assertContentEquals(bytes, picked.files.single().open().buffer().use { it.readByteArray() })
        assertEquals(false, presented?.second)
        assertTrue(presented?.first?.isNotEmpty() == true, "no uniform types were presented")
    }

    @Test
    fun pickManyPreservesThePickerOrder() = runTest {
        val first = file("b.wav", byteArrayOf(1))
        val second = file("a.flac", byteArrayOf(2))
        var multiple: Boolean? = null
        val result = picker(listOf(first, second), onPresent = { _, m -> multiple = m }).pickMany(AudioPickRequest.timelineAudio)
        val picked = assertIs<AudioPickResult.Picked>(result)
        assertEquals(listOf("b.wav", "a.flac"), picked.files.map { it.displayName })
        assertEquals(true, multiple)
    }

    @Test
    fun cancelAnswersCancelled() = runTest {
        assertEquals(AudioPickResult.Cancelled, picker(null).pickOne(AudioPickRequest.wav))
        assertEquals(AudioPickResult.Cancelled, picker(emptyList()).pickMany(AudioPickRequest.wav))
    }

    @Test
    fun securityScopedAccessIsReleasedAfterTheCopy() = runTest {
        val access = RecordingAccess()
        val url = file("Clave.wav", byteArrayOf(3, 4))
        val picked = assertIs<AudioPickResult.Picked>(picker(listOf(url), access).pickOne(AudioPickRequest.wav))
        assertEquals(emptyList(), access.log, "access began before the file was opened")
        val source = picked.files.single().open()
        assertEquals(listOf("begin Clave.wav"), access.log)
        source.buffer().use { it.readByteArray() }
        assertEquals(listOf("begin Clave.wav", "end Clave.wav"), access.log)
    }

    @Test
    fun theRequestsMimeTypesBecomeUniformTypes() {
        val types = IosAudioFilePicker.typesOf(AudioPickRequest.timelineAudio)
        assertTrue(types.isNotEmpty())
        assertTrue(types.any { it.preferredMIMEType == "audio/mpeg" }, "audio/mpeg has no uniform type")
    }
}
