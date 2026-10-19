package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.project.LibraryFilter
import audio.tembra.ui.designsystem.ChipAccent
import audio.tembra.ui.designsystem.ChipSpec
import audio.tembra.ui.designsystem.TembraChipRow
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags

/** "All" and "Recent" (spec FR-050): the design-system chip row; chips `projects.filter.<id>`. */
@Composable
internal fun LibraryFilterChips(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    val items = remember { listOf(ChipSpec(ALL, ProjectsCopy.filterAll), ChipSpec(RECENT, ProjectsCopy.filterRecent)) }
    TembraChipRow(
        items = items,
        selectedId = if (filter == LibraryFilter.All) ALL else RECENT,
        onSelect = { id -> onFilter(if (id == ALL) LibraryFilter.All else LibraryFilter.Recent) },
        accent = ChipAccent.Primary,
        modifier = modifier,
        testTag = ProjectsTestTags.FILTER,
    )
}

internal const val ALL = "all"
internal const val RECENT = "recent"
