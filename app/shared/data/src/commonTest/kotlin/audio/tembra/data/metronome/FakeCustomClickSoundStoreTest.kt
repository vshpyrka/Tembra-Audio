package audio.tembra.data.metronome

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The in-memory store double behaves like the Okio store: commit moves the staged bytes, the deletes are independent. */
class FakeCustomClickSoundStoreTest {

    private val store = FakeCustomClickSoundStore()

    @Test
    fun commitMovesTheStagedBytesIntoPlace() = runTest {
        assertFalse(store.commit())
        store.files[store.stagingPath] = byteArrayOf(1, 2)
        assertTrue(store.commit())
        assertTrue(store.exists())
        assertContentEquals(byteArrayOf(1, 2), store.files[store.path])
        assertFalse(store.stagingPath in store.files)
    }

    @Test
    fun deleteStagingKeepsTheCommittedCopy() = runTest {
        store.files[store.stagingPath] = byteArrayOf(1)
        assertTrue(store.commit())
        store.files[store.stagingPath] = byteArrayOf(9)
        store.deleteStaging()
        store.deleteStaging()
        assertFalse(store.stagingPath in store.files)
        assertContentEquals(byteArrayOf(1), store.files[store.path])
        assertTrue(store.exists())
        assertEquals(listOf("commit", "deleteStaging", "deleteStaging", "exists"), store.calls)
    }

    @Test
    fun deleteRemovesTheCommittedCopy() = runTest {
        store.files[store.stagingPath] = byteArrayOf(1)
        store.commit()
        store.delete()
        assertFalse(store.exists())
        assertFalse(store.path in store.files)
    }
}
