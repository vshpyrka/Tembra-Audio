package audio.tembra.ui.projects.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/** One value of the card's stats row ("124 BPM", "Dmin", "00:30.96", "4 tracks") as a small tabular chip. */
@Composable
internal fun ProjectStat(text: String, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Text(
        text = text,
        style = TembraTheme.typographyExtras.tabularParam,
        color = TembraTheme.colors.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .background(TembraTheme.colors.surfaceContainerHighest, RoundedCornerShape(TembraShapes.small))
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    )
}
