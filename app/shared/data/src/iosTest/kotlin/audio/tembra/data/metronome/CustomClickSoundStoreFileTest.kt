package audio.tembra.data.metronome

import audio.tembra.data.AppDirectories
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The custom sound store on the real file system. */
@OptIn(ExperimentalForeignApi::class)
class CustomClickSoundStoreFileTest {

    private fun directories(): AppDirectories {
        val root = NSTemporaryDirectory().trimEnd('/') + "/store-test-${Random.nextLong()}"
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
        return object : AppDirectories {
            override val documents = root
            override val cache = root
            override val preferences = root
        }
    }

    @Test
    fun aStagedKilobyteIsCommittedAndFound() = runTest {
        val store = OkioCustomClickSoundStore(directories())
        FileSystem.SYSTEM.write(store.stagingPath.toPath()) { write(ByteArray(1024) { it.toByte() }) }
        assertFalse(store.exists())
        assertTrue(store.commit())
        assertTrue(store.exists())
        assertEquals(1024L, FileSystem.SYSTEM.metadata(store.path.toPath()).size)
        store.delete()
        assertFalse(store.exists())
    }
}
