package audio.tembra.ui.drop

import audio.tembra.ui.platform.IosAudioFilePicker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import platform.Foundation.NSURL

/**
 * Audio opened with Tembra Audio from the Files app (feature 011, spec FR-026a; research R22): the shell's
 * `.onOpenURL` hands each file URL to [open]; each becomes one emission, kept until the shell collects it. The file is
 * read as the picker reads one (security-scoped access around the importer's copy).
 */
class IosOpenedFiles : OpenedFiles {
    private val requests = Channel<List<DroppedItem>>(Channel.BUFFERED)
    override val files: Flow<List<DroppedItem>> = requests.receiveAsFlow()

    fun open(url: NSURL) {
        val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: return
        val item = if (isAudioFileName(name)) DroppedItem.AudioFile(picker.pickedFile(url)) else DroppedItem.Other(name)
        requests.trySend(listOf(item))
    }

    private val picker = IosAudioFilePicker()
}
