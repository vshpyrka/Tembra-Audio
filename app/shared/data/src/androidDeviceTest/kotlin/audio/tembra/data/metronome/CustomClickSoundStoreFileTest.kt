package audio.tembra.data.metronome

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.data.AppDirectories
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The custom sound store on the real file system. */
class CustomClickSoundStoreFileTest {

    private fun directories(): AppDirectories {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "store-test-${Random.nextLong()}")
            .apply { mkdirs() }.absolutePath
        return object : AppDirectories {
            override val documents = root
            override val cache = root
            override val preferences = root
        }
    }

    @Test
    fun aStagedKilobyteIsCommittedAndFound() = runTest {
        val store = OkioCustomClickSoundStore(directories())
        File(store.stagingPath).writeBytes(ByteArray(1024) { it.toByte() })
        assertFalse(store.exists())
        assertTrue(store.commit())
        assertTrue(store.exists())
        assertEquals(1024L, File(store.path).length())
        store.delete()
        assertFalse(store.exists())
    }
}
