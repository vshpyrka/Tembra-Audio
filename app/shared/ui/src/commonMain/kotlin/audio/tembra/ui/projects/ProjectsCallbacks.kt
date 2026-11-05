package audio.tembra.ui.projects

import audio.tembra.core.project.LibraryFilter
import audio.tembra.core.project.LibrarySort
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.ProjectId
import audio.tembra.ui.drop.DropOutcome

/** The library's intents as plain functions (Constitution II); [None] for previews and tests. */
data class ProjectsCallbacks(
    val onQuery: (String) -> Unit,
    val onFilter: (LibraryFilter) -> Unit,
    val onSort: (LibrarySort) -> Unit,
    val onNewProject: () -> Unit,
    val onOpen: (ProjectId) -> Unit,
    val onRename: (ProjectId) -> Unit,
    val onDescribe: (ProjectId) -> Unit,
    val onKey: (ProjectId) -> Unit,
    val onDuplicate: (ProjectId) -> Unit,
    val onDelete: (ProjectId) -> Unit,
    val onRestoreDemo: () -> Unit,
    val onDialogDismiss: () -> Unit,
    val onRenameConfirm: (String) -> Unit,
    val onDescribeConfirm: (String) -> Unit,
    val onKeyConfirm: (MusicalKey?) -> Unit,
    val onDeleteConfirm: () -> Unit,
    /** Files dropped on the library (feature 011, spec FR-026). */
    val onDrop: (DropOutcome) -> Unit = {},
) {
    companion object {
        val None = ProjectsCallbacks(
            onQuery = {}, onFilter = {}, onSort = {}, onNewProject = {}, onOpen = {}, onRename = {}, onDescribe = {}, onKey = {},
            onDuplicate = {}, onDelete = {}, onRestoreDemo = {}, onDialogDismiss = {}, onRenameConfirm = {}, onDescribeConfirm = {},
            onKeyConfirm = {}, onDeleteConfirm = {},
        )
    }
}
