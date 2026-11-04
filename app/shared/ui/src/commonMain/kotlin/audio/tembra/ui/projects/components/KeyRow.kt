package audio.tembra.ui.projects.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import audio.tembra.ui.theme.TembraTheme

/** One row of the key picker: a radio-like selectable line, the chosen one in the primary tint. */
@Composable
internal fun KeyRow(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    Text(
        text = label,
        style = TembraTheme.typography.bodyLarge,
        color = if (selected) colors.primary else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTargetMin)
            .pointerHoverIcon(PointerIcon.Hand).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.sm)
            .testTag(testTag),
    )
}
