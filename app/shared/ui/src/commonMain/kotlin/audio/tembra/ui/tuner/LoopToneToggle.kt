package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.TembraToggle
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/** The "Loop Tone" container of the By Ear screens (research R9): overline, "Active" / "Off" and the switch (spec FR-017). */
@Composable
internal fun LoopToneToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val shape = RoundedCornerShape(TembraShapes.medium)
    Row(
        modifier = modifier
            .background(colors.surfaceContainerLow, shape)
            .border(spacing.seam, colors.outlineVariant.copy(alpha = BORDER_ALPHA), shape)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = spacing.sm)) {
            Text(text = TunerCopy.loopTone.uppercase(), style = TembraTheme.typographyExtras.microLabel, color = colors.secondary, maxLines = 1)
            Text(text = if (checked) TunerCopy.active else TunerCopy.off, style = TembraTheme.typographyExtras.microLabel, color = colors.onSurface, maxLines = 1)
        }
        TembraToggle(checked = checked, onCheckedChange = onCheckedChange, testTag = "tuner.loop")
    }
}

private const val BORDER_ALPHA = 0.3f
