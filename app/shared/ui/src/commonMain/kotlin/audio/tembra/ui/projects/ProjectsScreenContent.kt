package audio.tembra.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.EmptyState
import audio.tembra.ui.designsystem.FocusMemory
import audio.tembra.ui.designsystem.ScrollbarAdapter
import audio.tembra.ui.designsystem.TembraPill
import audio.tembra.ui.designsystem.focusRestoringRoot
import audio.tembra.ui.designsystem.tembraScrollbar
import audio.tembra.ui.projects.components.DeleteProjectDialog
import audio.tembra.ui.projects.components.DescribeProjectDialog
import audio.tembra.ui.projects.components.KeyPickerDialog
import audio.tembra.ui.projects.components.LibraryFilterChips
import audio.tembra.ui.projects.components.LibrarySearchField
import audio.tembra.ui.projects.components.ProjectCard
import audio.tembra.ui.projects.components.RenameProjectDialog
import audio.tembra.ui.projects.components.SortMenu
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless library (contracts/projects-library-screen.md; spec FR-050 to FR-053): the title with the count and
 * "New project", the search, the filter chips and the sort menu, then the cards in [layout]'s columns, the two empty
 * states, and the one open dialog. No transport bar. Root `screen.projects` with `stateDescription` "ready" once the
 * index is read.
 */
@Composable
internal fun ProjectsScreenContent(
    state: ProjectsUiState,
    callbacks: ProjectsCallbacks,
    layout: ProjectsLayout,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val ready = state.isFirstContentReady
    // Keyboard focus starts on the content root once the index is read (feature 011, spec FR-024).
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded) rootFocus.requestFocus()
    }
    val focusMemory = remember { FocusMemory() }
    val gridState = rememberLazyGridState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TembraTheme.daw.canvasLevel0)
            .testTag(ProjectsTestTags.ROOT)
            .semantics { stateDescription = if (ready) READY else LOADING }
            .focusRestoringRoot(focusMemory)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.pageMargin).padding(top = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ProjectsCopy.title, style = TembraTheme.typographyExtras.headlineLgMobile, color = colors.onSurface)
                    Text(state.countText, style = TembraTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.testTag(ProjectsTestTags.COUNT))
                }
                TembraPill(text = ProjectsCopy.newProject, selected = true, onClick = callbacks.onNewProject, testTag = ProjectsTestTags.NEW_PROJECT)
            }
            LibrarySearchField(text = state.query.text, onText = callbacks.onQuery, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LibraryFilterChips(filter = state.query.filter, onFilter = callbacks.onFilter, modifier = Modifier.width(FILTER_WIDTH))
                Spacer(Modifier.weight(1f))
                SortMenu(sort = state.query.sort, onSort = callbacks.onSort)
            }
            when {
                !state.loaded -> Unit
                state.isEmpty -> Column(modifier = Modifier.fillMaxWidth().testTag(ProjectsTestTags.EMPTY), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    EmptyState(ProjectsCopy.noProjects)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        TembraPill(text = ProjectsCopy.newProject, selected = true, onClick = callbacks.onNewProject)
                        TembraPill(text = ProjectsCopy.restoreDemo, selected = false, onClick = callbacks.onRestoreDemo, testTag = ProjectsTestTags.EMPTY_RESTORE)
                    }
                }
                state.noMatch -> EmptyState(ProjectsCopy.noMatch, modifier = Modifier.testTag(ProjectsTestTags.NO_MATCH))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(layout.columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ProjectsTestTags.GRID)
                        // A scrollbar while a mouse or trackpad scrolls the library (feature 011, spec FR-014a).
                        .tembraScrollbar(ScrollbarAdapter(gridState, layout.columns), Orientation.Vertical),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    items(state.cards, key = { it.id.value }) { card ->
                        ProjectCard(card = card, layout = layout, callbacks = callbacks)
                    }
                }
            }
        }
        when (val dialog = state.dialog) {
            is ProjectsDialog.Rename -> RenameProjectDialog(name = dialog.name, error = dialog.error, onConfirm = callbacks.onRenameConfirm, onDismiss = callbacks.onDialogDismiss)
            is ProjectsDialog.Describe -> DescribeProjectDialog(description = dialog.description, error = dialog.error, onConfirm = callbacks.onDescribeConfirm, onDismiss = callbacks.onDialogDismiss)
            is ProjectsDialog.Key -> KeyPickerDialog(selected = dialog.key, onConfirm = callbacks.onKeyConfirm, onDismiss = callbacks.onDialogDismiss)
            is ProjectsDialog.Delete -> DeleteProjectDialog(name = dialog.name, onConfirm = callbacks.onDeleteConfirm, onCancel = callbacks.onDialogDismiss)
            null -> Unit
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

internal const val READY = "ready"
internal const val LOADING = "loading"
private val FILTER_WIDTH = 176.dp
