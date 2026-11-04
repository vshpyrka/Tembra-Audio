package audio.tembra.data.projects

import audio.tembra.engine.FakeAudioEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The 200 MB refusal before a take (contracts/take-storage.md "Free space"; spec FR-025) over the engine's probe. */
class TakeFreeSpaceTest {

    private val engine = FakeAudioEngine()
    private val freeSpace = TakeFreeSpace(engine)

    @Test
    fun reportsTheEnginesAnswerAndRefusesBelowTwoHundredMegabytes() {
        engine.freeBytesResult = 1024L * 1024 * 1024
        assertEquals(1024L * 1024 * 1024, freeSpace.freeBytes("/documents/projects/p1/audio"))
        assertTrue(freeSpace.canRecord("/documents/projects/p1/audio"))
        engine.freeBytesResult = TakeFreeSpace.RECORD_MIN_FREE_BYTES
        assertTrue(freeSpace.canRecord("/documents/projects/p1/audio"), "exactly 200 MB still records")
        engine.freeBytesResult = TakeFreeSpace.RECORD_MIN_FREE_BYTES - 1
        assertFalse(freeSpace.canRecord("/documents/projects/p1/audio"))
        engine.freeBytesResult = 0
        assertFalse(freeSpace.canRecord("/documents/projects/p1/audio"))
    }

    @Test
    fun anUnknownAnswerDoesNotRefuse() {
        engine.freeBytesResult = -1
        assertEquals(-1L, freeSpace.freeBytes("/documents/projects/p1/audio"))
        assertTrue(freeSpace.canRecord("/documents/projects/p1/audio"), "the engine ends a take itself at its own floor")
        assertEquals(200L * 1024 * 1024, TakeFreeSpace.RECORD_MIN_FREE_BYTES)
    }
}
