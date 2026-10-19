package audio.tembra.ui.projects.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.MusicalKey
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/** The project key (spec FR-052): "None" and the 24 major and minor keys; choosing one confirms at once. Rows `dialog.projectKey.<name>`. */
@Composable
internal fun KeyPickerDialog(selected: MusicalKey?, onConfirm: (MusicalKey?) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ProjectsTestTags.DIALOG_CANCEL)) {
                Text(ProjectsCopy.cancel, color = colors.onSurfaceVariant, style = TembraTheme.typography.labelLarge)
            }
        },
        title = { Text(ProjectsCopy.key, style = TembraTheme.typography.headlineSmall, color = colors.onSurface) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                KeyRow(label = ProjectsCopy.keyNone, selected = selected == null, onClick = { onConfirm(null) }, testTag = ProjectsTestTags.keyOption("none"))
                for (key in MusicalKey.entries) {
                    KeyRow(label = key.displayName, selected = key == selected, onClick = { onConfirm(key) }, testTag = ProjectsTestTags.keyOption(key.name))
                }
            }
        },
        modifier = modifier.testTag(ProjectsTestTags.KEY_DIALOG),
        shape = RoundedCornerShape(TembraShapes.large),
        containerColor = colors.surfaceContainerHigh,
    )
}

@Composable
private fun KeyRow(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    Text(
        text = label,
        style = TembraTheme.typography.bodyLarge,
        color = if (selected) colors.primary else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTargetMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.sm)
            .testTag(testTag),
    )
}

private val LIST_MAX_HEIGHT = 360.dp
