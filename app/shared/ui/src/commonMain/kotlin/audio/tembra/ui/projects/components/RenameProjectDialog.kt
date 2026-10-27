package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.project.ProjectNames
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags

/** Rename (spec FR-052): 1 to 60 characters; an empty name is refused by the ViewModel and reads [error]. */
@Composable
internal fun RenameProjectDialog(name: String, error: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ProjectTextDialog(
        title = ProjectsCopy.rename,
        label = ProjectsCopy.nameLabel,
        initial = name,
        maxLength = ProjectNames.MAX_PROJECT_NAME,
        error = error,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTag = ProjectsTestTags.RENAME_DIALOG,
        modifier = modifier,
    )
}
