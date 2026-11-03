package audio.tembra.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.shortcuts.RegisterOverlay

/**
 * The app's confirmation dialog (contracts/design-system-additions.md): [title] in `headlineSmall`, [text] in
 * `bodyMedium`, [dismiss] on the left and [confirm] on the right as text buttons in `primary`; dismissing by tapping
 * outside or with back runs [dismiss]'s action, so a "keep going" answer is never skipped. The dialog surface is
 * `surfaceContainerHigh` with the `large` radius; [testTag] goes on the surface.
 */
@Composable
fun TembraDialog(
    title: String,
    text: String,
    confirm: DialogAction,
    dismiss: DialogAction,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    // Escape's close-overlay action answers as dismissing does (feature 011, contracts/keyboard-shortcuts.md rule 4).
    RegisterOverlay(dismiss.onClick)
    AlertDialog(
        onDismissRequest = dismiss.onClick,
        confirmButton = {
            TextButton(onClick = confirm.onClick, modifier = Modifier.testTagIfSet(confirm.testTag)) {
                Text(confirm.label, color = colors.primary, style = TembraTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss.onClick, modifier = Modifier.testTagIfSet(dismiss.testTag)) {
                Text(dismiss.label, color = colors.onSurfaceVariant, style = TembraTheme.typography.labelLarge)
            }
        },
        title = { Text(title, style = TembraTheme.typography.headlineSmall, color = colors.onSurface) },
        text = { Text(text, style = TembraTheme.typography.bodyMedium, color = colors.onSurfaceVariant) },
        modifier = modifier.anchorRoot().testTagIfSet(testTag),
        shape = RoundedCornerShape(TembraShapes.large),
        containerColor = colors.surfaceContainerHigh,
    )
}
