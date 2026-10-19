package audio.tembra.ui.projects

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.LibraryQuery
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the library renders (spec FR-050 to FR-053): the cards after the query, the count text, the open dialog. */
@Immutable
data class ProjectsUiState(
    /** False until the library index has been read once. */
    val loaded: Boolean = false,
    val cards: ImmutableList<ProjectCardUi> = persistentListOf(),
    /** Every project on the device, before the query narrows them. */
    val total: Int = 0,
    val query: LibraryQuery = LibraryQuery(),
    val countText: String = "",
    val dialog: ProjectsDialog? = null,
) {
    /** The library holds no project at all (only possible after deleting the demo). */
    val isEmpty: Boolean get() = loaded && total == 0
    /** Projects exist but none matches the query. */
    val noMatch: Boolean get() = loaded && total > 0 && cards.isEmpty()
    val isFirstContentReady: Boolean get() = loaded
}
