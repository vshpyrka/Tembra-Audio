package audio.tembra.ui.drop

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/** The banner naming what a drop will do while files hover (feature 011, spec FR-026): "New track", "Add to Drums at bar 5". */
@Composable
fun DropHoverBanner(text: String, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier.padding(spacing.md).testTag(DROP_HOVER_TAG),
        shape = RoundedCornerShape(TembraShapes.extraLarge),
        color = TembraTheme.colors.primaryContainer,
        contentColor = TembraTheme.colors.onPrimaryContainer,
    ) {
        Text(text, style = TembraTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm))
    }
}

/** The hover banner's tag, wherever a drop zone shows it. */
const val DROP_HOVER_TAG = "drop.hover"
