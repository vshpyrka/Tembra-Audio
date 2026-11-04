package audio.tembra.ui.drop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSDate
import platform.Foundation.NSItemProvider
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.concurrent.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * What a drop onto the iPad holds (feature 011, spec FR-026; research R12): each item's provider is read with a file
 * representation copied into the app's cache inside the callback (the system removes its own copy after it returns).
 * An audio file becomes a picked file whose bytes are the dropped file's; a folder its direct children (audio files,
 * other files by name, folders inside it named, not entered); anything else is named. The test binary runs on the main
 * thread without spinning its run loop, where the providers answer, so [read] spins it while the reader works.
 */
class IosDroppedFilesReaderTest {

    private val fileSystem = FileSystem.SYSTEM
    private val root = NSTemporaryDirectory().trimEnd('/') + "/drop-test-${Random.nextLong()}"
    private val cache = "$root/cache"
    private val reader = IosDroppedFilesReader(cacheDirectory = cache)

    init {
        fileSystem.createDirectories("$root/source".toPath())
    }

    @AfterTest
    fun removeTheFiles() {
        fileSystem.deleteRecursively(root.toPath())
    }

    /** Runs [block] off the main thread and spins the main run loop until it answers (ten seconds at most). */
    private fun <T> read(block: suspend () -> T): T {
        val answer = AtomicReference<Result<T>?>(null)
        CoroutineScope(Dispatchers.Default).launch { answer.value = runCatching { block() } }
        val started = TimeSource.Monotonic.markNow()
        while (answer.value == null && started.elapsedNow() < 10.seconds) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        return checkNotNull(answer.value) { "the reader did not answer in ten seconds" }.getOrThrow()
    }

    private fun file(relative: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): NSURL {
        val path = "$root/source/$relative".toPath()
        path.parent?.let(fileSystem::createDirectories)
        fileSystem.write(path) { write(bytes) }
        return NSURL.fileURLWithPath(path.toString())
    }

    @Test
    fun anAudioFileIsCopiedIntoTheCacheAndKeepsItsName() {
        val url = file("kick.wav", byteArrayOf(7, 8, 9))
        val item = assertIs<DroppedItem.AudioFile>(read { reader.itemOf(assertNotNull(NSItemProvider(contentsOfURL = url))) })
        assertEquals("kick.wav", item.file.displayName)
        fileSystem.delete("$root/source/kick.wav".toPath())
        val bytes = read { item.file.open().buffer().use { it.readByteArray() } }
        assertContentEquals(byteArrayOf(7, 8, 9), bytes)
    }

    @Test
    fun aFolderGivesItsDirectChildren() {
        file("Stems/1.wav")
        file("Stems/2.mp3")
        file("Stems/notes.txt")
        file("Stems/Old/x.wav")
        val folderUrl = NSURL.fileURLWithPath("$root/source/Stems", isDirectory = true)
        val folder = assertIs<DroppedItem.Folder>(read { reader.itemOf(assertNotNull(NSItemProvider(contentsOfURL = folderUrl))) })
        assertEquals("Stems", folder.name)
        val contents = assertNotNull(folder.contents, "the folder's contents are readable")
        val names = contents.map { child ->
            when (child) {
                is DroppedItem.AudioFile -> "audio:${child.file.displayName}"
                is DroppedItem.Folder -> "folder:${child.name}"
                is DroppedItem.Other -> "other:${child.name}"
            }
        }
        assertEquals(listOf("audio:1.wav", "audio:2.mp3", "folder:Old", "other:notes.txt"), names.sorted())
    }

    @Test
    fun anythingElseIsNamed() {
        val url = file("cover.png")
        assertEquals(DroppedItem.Other("cover.png"), read { reader.itemOf(assertNotNull(NSItemProvider(contentsOfURL = url))) })
    }
}
