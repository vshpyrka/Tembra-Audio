package audio.tembra.data.metronome

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The custom click sound's staged-then-committed file over a fake file system (data-model.md section 5). */
class OkioCustomClickSoundStoreTest {

    private val fileSystem = FakeFileSystem()
    private val store = OkioCustomClickSoundStore(FakeAppDirectories(), fileSystem)

    private fun stage(bytes: ByteArray) {
        fileSystem.write(store.stagingPath.toPath()) { write(bytes) }
    }

    @Test
    fun pathsSitUnderTheDocumentsDirectory() {
        assertEquals("/documents/metronome/custom.wav", store.path)
        assertEquals(store.path + ".tmp", store.stagingPath)
    }

    @Test
    fun theMetronomeDirectoryIsCreatedOnFirstUse() {
        assertTrue(fileSystem.exists("/documents/metronome".toPath()))
    }

    @Test
    fun commitMovesTheStagedFileIntoPlace() = runTest {
        assertFalse(store.exists())
        stage(byteArrayOf(1, 2, 3))
        assertTrue(store.commit())
        assertTrue(store.exists())
        assertContentEquals(byteArrayOf(1, 2, 3), fileSystem.read(store.path.toPath()) { readByteArray() })
        assertFalse(fileSystem.exists(store.stagingPath.toPath()))
    }

    @Test
    fun aSecondCommitReplacesTheContent() = runTest {
        stage(byteArrayOf(1, 2, 3))
        assertTrue(store.commit())
        stage(byteArrayOf(9, 8))
        assertTrue(store.commit())
        assertContentEquals(byteArrayOf(9, 8), fileSystem.read(store.path.toPath()) { readByteArray() })
    }

    @Test
    fun commitWithoutAStagingFileReturnsFalseAndKeepsTheCurrentFile() = runTest {
        assertFalse(store.commit())
        stage(byteArrayOf(5))
        assertTrue(store.commit())
        assertFalse(store.commit())
        assertContentEquals(byteArrayOf(5), fileSystem.read(store.path.toPath()) { readByteArray() })
    }

    @Test
    fun deleteRemovesTheFileAndIsIdempotent() = runTest {
        store.delete()
        stage(byteArrayOf(1))
        assertTrue(store.commit())
        store.delete()
        assertFalse(store.exists())
        store.delete()
        assertFalse(store.exists())
    }

    @Test
    fun deleteStagingRemovesOnlyTheStagedFileAndIsIdempotent() = runTest {
        store.deleteStaging()
        stage(byteArrayOf(4, 4))
        assertTrue(store.commit())
        stage(byteArrayOf(7))
        store.deleteStaging()
        assertFalse(fileSystem.exists(store.stagingPath.toPath()))
        assertContentEquals(byteArrayOf(4, 4), fileSystem.read(store.path.toPath()) { readByteArray() })
        store.deleteStaging()
        assertTrue(store.exists())
        assertFalse(store.commit())
    }

    // --- Staging from a picked file (feature 009: the platform picker hands over a content handle) -------------------

    @Test
    fun stageCopiesThePickedBytesToTheStagingPath() = runTest {
        assertEquals(StageResult.Staged, store.stage { Buffer().write(byteArrayOf(5, 6, 7)) })
        assertContentEquals(byteArrayOf(5, 6, 7), fileSystem.read(store.stagingPath.toPath()) { readByteArray() })
        assertFalse(store.exists())
    }

    @Test
    fun stageRefusesAnUnreadableSourceAndLeavesNothingStaged() = runTest {
        assertEquals(StageResult.Unreadable, store.stage { throw okio.IOException("revoked") })
        assertFalse(fileSystem.exists(store.stagingPath.toPath()))
    }
}
