package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.project.ProjectNames
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags

/** The one-line description (spec FR-052): up to 120 characters, empty allowed. */
@Composable
internal fun DescribeProjectDialog(description: String, error: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ProjectTextDialog(
        title = ProjectsCopy.description,
        label = ProjectsCopy.descriptionLabel,
        initial = description,
        maxLength = ProjectNames.MAX_DESCRIPTION,
        error = error,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTag = ProjectsTestTags.DESCRIBE_DIALOG,
        modifier = modifier,
    )
}
