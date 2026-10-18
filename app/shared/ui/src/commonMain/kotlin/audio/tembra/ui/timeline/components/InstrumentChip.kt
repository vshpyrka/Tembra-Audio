package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.clearAndSetSemantics
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import androidx.compose.ui.unit.dp

/** A track's instrument label as a small pill in `labelSmall` (spec FR-021): the glyph where one exists, then the name. Drawn, not interactive: the header's menu changes it. */
@Composable
internal fun InstrumentChip(label: InstrumentLabel, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val fill = colors.surfaceContainerHighest
    Row(
        modifier = modifier
            .height(ChipHeight)
            .drawBehind { drawRoundRect(fill, cornerRadius = CornerRadius(size.height / 2)) }
            .padding(horizontal = spacing.sm)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentGlyphs.of(label)?.let { icon ->
            Icon(imageVector = TembraIcons.vector(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(spacing.segmentIconSmall))
        }
        Text(label.displayName, style = TembraTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

private val ChipHeight = 20.dp
