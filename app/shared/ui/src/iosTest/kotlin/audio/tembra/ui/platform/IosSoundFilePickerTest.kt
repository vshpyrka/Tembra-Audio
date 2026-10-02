package audio.tembra.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import platform.posix.truncate
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** The copy step of the iOS picker (contracts/click-sounds.md): the presented controller itself is exercised by hand. */
@OptIn(ExperimentalForeignApi::class)
class IosSoundFilePickerTest {

    private val root = NSTemporaryDirectory().trimEnd('/') + "/picker-test-${Random.nextLong()}"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeTheFiles() {
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun aKilobyteFileIsCopiedAndNamedWithoutItsExtension() {
        val bytes = ByteArray(1024) { it.toByte() }
        val source = "$root/Wood Block.wav"
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(source, contents = data, attributes = null)
        val destination = "$root/staging/custom.wav"

        val result = IosSoundFilePicker.copyPicked(NSURL.fileURLWithPath(source), destination)

        assertEquals(SoundPickResult.Picked("Wood Block"), result)
        val copied = assertNotNull(NSData.dataWithContentsOfFile(destination))
        val copiedBytes = ByteArray(copied.length.toInt())
        copiedBytes.usePinned { memcpy(it.addressOf(0), copied.bytes, copied.length) }
        assertContentEquals(bytes, copiedBytes)
    }

    @Test
    fun aFileOverSixtyFourMegabytesIsRefused() {
        val source = "$root/huge.wav"
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)
        truncate(source, 65L * 1024 * 1024)
        val destination = "$root/custom.wav"

        val result = IosSoundFilePicker.copyPicked(NSURL.fileURLWithPath(source), destination)

        assertEquals(SoundPickResult.Failed(SoundPickFailure.TooLarge), result)
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(destination))
    }
}
