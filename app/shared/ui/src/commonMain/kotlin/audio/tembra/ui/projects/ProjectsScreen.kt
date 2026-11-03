package audio.tembra.ui.projects

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.ui.perf.ReportFirstContentDrawn
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Projects destination's wiring (contracts/projects-library-screen.md): the ViewModel from the Metro graph, the
 * layout from the screen's width, the one-shot messages into the snackbar, and the open event into the shell
 * ([onOpenTimeline]). First content is reported once the index is read (feature 007).
 */
@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = metroViewModel(),
    onOpenTimeline: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportFirstContentDrawn(state.isFirstContentReady)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.text) }
    }
    LaunchedEffect(viewModel, onOpenTimeline) {
        viewModel.events.collect { event ->
            when (event) {
                ProjectsEvent.OpenedInTimeline -> onOpenTimeline()
            }
        }
    }
    val callbacks = remember(viewModel) {
        ProjectsCallbacks(
            onQuery = viewModel::setQuery,
            onFilter = viewModel::setFilter,
            onSort = viewModel::setSort,
            onNewProject = viewModel::newProject,
            onOpen = viewModel::open,
            onRename = viewModel::rename,
            onDescribe = viewModel::describe,
            onKey = viewModel::chooseKey,
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
            onRestoreDemo = viewModel::restoreDemo,
            onDialogDismiss = viewModel::dismissDialog,
            onRenameConfirm = viewModel::confirmRename,
            onDescribeConfirm = viewModel::confirmDescription,
            onKeyConfirm = viewModel::confirmKey,
            onDeleteConfirm = viewModel::confirmDelete,
            onDrop = viewModel::drop,
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        ProjectsScreenContent(
            state = state,
            callbacks = callbacks,
            layout = ProjectsLayout.from(maxWidth),
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
