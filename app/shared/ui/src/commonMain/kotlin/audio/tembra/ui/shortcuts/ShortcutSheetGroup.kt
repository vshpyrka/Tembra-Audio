package audio.tembra.ui.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * One scope of the [ShortcutSheet]: its [title] over [rows], each a label and its chords (alternatives separated), every
 * chord as key caps.
 */
@Composable
internal fun ShortcutSheetGroup(title: String, rows: List<Pair<String, List<List<String>>>>, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(title, style = TembraTheme.typography.titleSmall, color = colors.primary)
        for ((label, chords) in rows) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(label, style = TembraTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                chords.forEachIndexed { index, caps ->
                    if (index > 0) Text("/", style = TembraTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    caps.forEach { cap ->
                        Text(
                            text = cap,
                            style = TembraTheme.typography.labelMedium,
                            color = colors.onSurface,
                            modifier = Modifier
                                .background(colors.surfaceContainerHighest, RoundedCornerShape(TembraShapes.small))
                                .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}
