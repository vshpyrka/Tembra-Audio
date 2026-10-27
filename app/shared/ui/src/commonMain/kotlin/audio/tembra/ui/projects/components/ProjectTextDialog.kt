package audio.tembra.ui.projects.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.TembraTextField
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * A dialog with one text field (spec FR-052): the rename and the description. The field keeps the draft; Save hands
 * it to [onConfirm], and [error] (the ViewModel's refusal) shows under the field. Tagged [testTag]; the field
 * `dialog.field`, Save `dialog.confirm`, Cancel `dialog.cancel`.
 */
@Composable
internal fun ProjectTextDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, modifier = Modifier.testTag(ProjectsTestTags.DIALOG_CONFIRM)) {
                Text(ProjectsCopy.save, color = colors.primary, style = TembraTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ProjectsTestTags.DIALOG_CANCEL)) {
                Text(ProjectsCopy.cancel, color = colors.onSurfaceVariant, style = TembraTheme.typography.labelLarge)
            }
        },
        title = { Text(title, style = TembraTheme.typography.headlineSmall, color = colors.onSurface) },
        text = {
            TembraTextField(
                value = draft,
                onValueChange = { draft = it },
                label = label,
                maxLength = maxLength,
                error = error,
                testTag = ProjectsTestTags.DIALOG_FIELD,
            )
        },
        modifier = modifier.anchorRoot().testTag(testTag),
        shape = RoundedCornerShape(TembraShapes.large),
        containerColor = colors.surfaceContainerHigh,
    )
}
