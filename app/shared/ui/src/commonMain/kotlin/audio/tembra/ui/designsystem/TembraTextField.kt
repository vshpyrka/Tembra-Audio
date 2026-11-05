package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.shortcuts.LocalTextInputFocus
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The app's text field for names, descriptions and search (contracts/design-system-additions.md): an outlined field
 * in the scheme's colours with the `medium` radius; [maxLength] is enforced here (a longer change is dropped, so the
 * caller never sees an over-long value); [error] renders in `error` under the field and marks the node with the
 * error semantics; [leading] and [trailing] are the caller's glyphs (a search icon, a clear button). [testTag] goes
 * on the field, `<testTag>.error` on the error text.
 */
@Composable
fun TembraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLength: Int = Int.MAX_VALUE,
    error: String? = null,
    placeholder: String? = null,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // The shell's chords wait while a text field has focus, so its characters are typed (feature 011, spec FR-023).
    val textInput = LocalTextInputFocus.current
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { next -> if (next.length <= maxLength) onValueChange(next) },
            modifier = Modifier
                .fillMaxWidth()
                .testTagIfSet(testTag)
                .onFocusChanged { textInput?.report(it.isFocused) }
                .semantics { if (error != null) this.error(error) },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = colors.onSurfaceVariant) } },
            leadingIcon = leading,
            trailingIcon = trailing,
            isError = error != null,
            singleLine = singleLine,
            interactionSource = source,
            shape = RoundedCornerShape(TembraShapes.medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                errorBorderColor = colors.error,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant,
                cursorColor = colors.primary,
            ),
        )
        if (error != null) {
            Text(
                text = error,
                style = TembraTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = spacing.md, top = spacing.xs).testTagIfSet(testTag?.let { "$it.error" }),
            )
        }
    }
}
