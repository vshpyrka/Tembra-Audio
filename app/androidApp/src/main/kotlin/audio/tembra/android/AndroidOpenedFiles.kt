package audio.tembra.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import audio.tembra.ui.drop.DroppedItem
import audio.tembra.ui.drop.OpenedFiles
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Audio opened with Tembra Audio from a file manager or ChromeOS Files (feature 011, spec FR-026a; research R22):
 * [MainActivity] delivers each `ACTION_VIEW` intent (its data and every `ClipData` URI) from `onCreate` and
 * `onNewIntent`; each becomes one emission, kept until the shell collects it, each delivered once.
 */
class AndroidOpenedFiles(private val context: Context) : OpenedFiles {
    private val requests = Channel<List<DroppedItem>>(Channel.BUFFERED)
    override val files: Flow<List<DroppedItem>> = requests.receiveAsFlow()

    /** True when [intent] asked to open files and they were queued. */
    fun deliver(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val uris = urisOf(intent)
        if (uris.isEmpty()) return false
        val resolver = context.contentResolver
        // One file: the intent names its type; several: each URI's provider does.
        val items = uris.map { uri -> AndroidDroppedFilesReader.itemOf(uri, intent.type?.takeIf { uris.size == 1 } ?: resolver.getType(uri), resolver) }
        return requests.trySend(items).isSuccess
    }

    private fun urisOf(intent: Intent): List<Uri> = buildList {
        intent.data?.let(::add)
        val clip = intent.clipData ?: return@buildList
        for (index in 0 until clip.itemCount) {
            val uri = clip.getItemAt(index).uri ?: continue
            if (uri !in this) add(uri)
        }
    }
}
