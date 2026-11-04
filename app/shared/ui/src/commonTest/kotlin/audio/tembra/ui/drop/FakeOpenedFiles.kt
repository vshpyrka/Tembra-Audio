package audio.tembra.ui.drop

import audio.tembra.data.projects.PickedFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Files handed to the app by the system, emitted by the test (feature 011). */
class FakeOpenedFiles : OpenedFiles {
    private val emitted = MutableSharedFlow<List<DroppedItem>>(extraBufferCapacity = 4)
    override val files: SharedFlow<List<DroppedItem>> = emitted

    fun open(vararg items: DroppedItem) {
        check(emitted.tryEmit(items.toList()))
    }
}

/** Records each new project made from files; [succeeds] is what the next start reports. */
class FakeProjectFromAudio(var succeeds: Boolean = true) : ProjectFromAudio {
    val started = mutableListOf<Pair<List<String>, List<SkippedItem>>>()

    override suspend fun start(files: List<PickedFile>, skipped: List<SkippedItem>): Boolean {
        started += files.map { it.displayName } to skipped
        return succeeds
    }
}
