package audio.tembra.ui.projects.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.MusicalKey
import audio.tembra.ui.designsystem.verticalScrollWithScrollbar
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.shortcuts.RegisterOverlay

/** The project key (spec FR-052): "None" and the 24 major and minor keys; choosing one confirms at once. Rows `dialog.projectKey.<name>`. */
@Composable
internal fun KeyPickerDialog(selected: MusicalKey?, onConfirm: (MusicalKey?) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    RegisterOverlay(onDismiss)
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
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT).verticalScrollWithScrollbar(rememberScrollState())) {
                KeyRow(label = ProjectsCopy.keyNone, selected = selected == null, onClick = { onConfirm(null) }, testTag = ProjectsTestTags.keyOption("none"))
                for (key in MusicalKey.entries) {
                    KeyRow(label = key.displayName, selected = key == selected, onClick = { onConfirm(key) }, testTag = ProjectsTestTags.keyOption(key.name))
                }
            }
        },
        modifier = modifier.anchorRoot().testTag(ProjectsTestTags.KEY_DIALOG),
        shape = RoundedCornerShape(TembraShapes.large),
        containerColor = colors.surfaceContainerHigh,
    )
}

private val LIST_MAX_HEIGHT = 360.dp
