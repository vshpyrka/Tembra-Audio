package audio.tembra.ui.projects

import audio.tembra.core.project.LibraryQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow

/** The Projects library's search, filter and sort, kept while the app runs and reset by a relaunch (spec FR-053). */
@Inject
@SingleIn(AppScope::class)
class LibraryViewStateHolder {
    val query: MutableStateFlow<LibraryQuery> = MutableStateFlow(LibraryQuery())
}
