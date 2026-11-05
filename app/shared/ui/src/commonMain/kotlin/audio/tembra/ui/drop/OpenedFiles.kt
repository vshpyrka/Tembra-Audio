package audio.tembra.ui.drop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Files the system hands the app to open (feature 011, spec FR-026a; research R22): "Open with" from Android's file
 * managers and ChromeOS Files, and the iPad's Files app. Each emission is one request; the shell makes it a new project
 * ([ProjectFromAudio]). Bound per platform: `AndroidOpenedFiles`, `IosOpenedFiles`.
 */
interface OpenedFiles {
    val files: Flow<List<DroppedItem>>
}

/** A platform or test that hands the app no files. */
object NoOpenedFiles : OpenedFiles {
    override val files: Flow<List<DroppedItem>> = emptyFlow()
}
