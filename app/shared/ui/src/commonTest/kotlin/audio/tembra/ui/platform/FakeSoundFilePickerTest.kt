package audio.tembra.ui.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The picker double records destinations and writes its bytes only for a pick. */
class FakeSoundFilePickerTest {

    @Test
    fun pickedWritesTheBytesToTheDestination() = runTest {
        val picker = FakeSoundFilePicker(next = SoundPickResult.Picked("Kick"), bytesToWrite = byteArrayOf(1, 2, 3))
        assertEquals(SoundPickResult.Picked("Kick"), picker.pickWav("/staging.wav"))
        assertEquals(listOf("/staging.wav"), picker.destinations)
        assertContentEquals(byteArrayOf(1, 2, 3), picker.files["/staging.wav"])
    }

    @Test
    fun cancelledAndFailedWriteNothing() = runTest {
        val picker = FakeSoundFilePicker(bytesToWrite = byteArrayOf(1))
        assertEquals(SoundPickResult.Cancelled, picker.pickWav("/a.wav"))
        picker.next = SoundPickResult.Failed(SoundPickFailure.TooLarge)
        assertEquals(SoundPickResult.Failed(SoundPickFailure.TooLarge), picker.pickWav("/b.wav"))
        assertEquals(listOf("/a.wav", "/b.wav"), picker.destinations)
        assertTrue(picker.files.isEmpty())
    }
}
