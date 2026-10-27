package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.project.LibrarySort
import audio.tembra.ui.metronome.components.MenuEntry
import audio.tembra.ui.metronome.components.MenuPill
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags

/** "Last edited" (the default) or "Name" (spec FR-050) on the design-system menu pill; entries `projects.sort.<id>`. */
@Composable
internal fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    val entries = remember { listOf(MenuEntry(LAST_EDITED, ProjectsCopy.sortLastEdited), MenuEntry(NAME, ProjectsCopy.sortName)) }
    val label = if (sort == LibrarySort.LastEdited) ProjectsCopy.sortLastEdited else ProjectsCopy.sortName
    MenuPill(
        label = label,
        description = "${ProjectsCopy.sortBy} $label",
        entries = entries,
        selectedId = if (sort == LibrarySort.LastEdited) LAST_EDITED else NAME,
        onSelect = { id -> onSort(if (id == NAME) LibrarySort.Name else LibrarySort.LastEdited) },
        testTag = ProjectsTestTags.SORT,
        modifier = modifier,
    )
}

internal const val LAST_EDITED = "lastEdited"
internal const val NAME = "name"
