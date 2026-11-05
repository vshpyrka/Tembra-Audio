package audio.tembra.ui.drop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Audio opened with the app from the Files app (feature 011, spec FR-026a; research R22): the shell's `.onOpenURL`
 * hands each file URL to [IosOpenedFiles], which emits it once, as an audio file or, if it is not audio, by name.
 */
class IosOpenedFilesTest {

    @Test
    fun anAudioFileUrlIsOneAudioFileEmission() = runTest {
        val opened = IosOpenedFiles()
        opened.open(NSURL.fileURLWithPath("/tmp/Inbox/kick.wav"))
        val item = assertIs<DroppedItem.AudioFile>(opened.files.first().single())
        assertEquals("kick.wav", item.file.displayName)
    }

    @Test
    fun aNonAudioUrlIsNamed() = runTest {
        val opened = IosOpenedFiles()
        opened.open(NSURL.fileURLWithPath("/tmp/Inbox/notes.txt"))
        assertEquals(listOf(DroppedItem.Other("notes.txt")), opened.files.first())
    }
}
