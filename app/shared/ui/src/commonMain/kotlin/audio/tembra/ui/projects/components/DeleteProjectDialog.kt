package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.DialogAction
import audio.tembra.ui.designsystem.TembraDialog
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags

/** Delete confirms (spec FR-052): the project and its audio copies leave the device. */
@Composable
internal fun DeleteProjectDialog(name: String, onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TembraDialog(
        title = ProjectsCopy.deleteTitle,
        text = ProjectsCopy.deleteText(name),
        confirm = DialogAction(ProjectsCopy.delete, onClick = onConfirm, testTag = ProjectsTestTags.DIALOG_CONFIRM),
        dismiss = DialogAction(ProjectsCopy.cancel, onClick = onCancel, testTag = ProjectsTestTags.DIALOG_CANCEL),
        modifier = modifier,
        testTag = ProjectsTestTags.DELETE_DIALOG,
    )
}
