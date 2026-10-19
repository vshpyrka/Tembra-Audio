package audio.tembra.ui.projects.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.TembraTextField
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportButton

/** The library's search (spec FR-053): a text field with the search glyph and a clear control once there is text. Tagged `projects.search`. */
@Composable
internal fun LibrarySearchField(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    TembraTextField(
        value = text,
        onValueChange = onText,
        label = ProjectsCopy.search,
        modifier = modifier,
        testTag = ProjectsTestTags.SEARCH,
        leading = {
            Icon(
                imageVector = TembraIcons.vector(TembraIcon.Search),
                contentDescription = null,
                tint = TembraTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(spacing.pickerIconSize),
            )
        },
        trailing = if (text.isEmpty()) null else {
            { TransportButton(icon = TembraIcon.Remove, description = ProjectsCopy.clearSearch, onClick = { onText("") }, testTag = ProjectsTestTags.SEARCH_CLEAR) }
        },
    )
}
