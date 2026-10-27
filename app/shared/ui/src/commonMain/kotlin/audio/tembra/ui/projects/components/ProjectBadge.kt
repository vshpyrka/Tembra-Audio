package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.TembraStatusPill
import audio.tembra.ui.theme.TembraTheme

/** A card's badge (spec FR-051): "Active" in the primary tint on the open project, "Demo" in the secondary one. */
@Composable
internal fun ProjectBadge(text: String, accent: Boolean, modifier: Modifier = Modifier, testTag: String? = null) {
    val colors = TembraTheme.colors
    TembraStatusPill(text = text, color = if (accent) colors.primary else colors.secondary, modifier = modifier, testTag = testTag)
}
