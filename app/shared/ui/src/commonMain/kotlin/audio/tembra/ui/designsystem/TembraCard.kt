package audio.tembra.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The project card's and settings form's surface (contracts/design-system-additions.md; DESIGN.md surface levels):
 * `surface_level1` with the 1 px `surface_level2_border` seam at rest, `surface_level2` when [elevated] (the hovered
 * or active card), the `large` radius. [content] is the caller's; the card adds no padding so a chart can bleed to the
 * edge. [testTag] goes on the surface.
 */
@Composable
fun TembraCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier.testTagIfSet(testTag),
        shape = RoundedCornerShape(TembraShapes.large),
        color = if (elevated) daw.surfaceLevel2 else daw.surfaceLevel1,
        contentColor = TembraTheme.colors.onSurface,
        border = BorderStroke(spacing.seam, daw.surfaceLevel2Border),
    ) {
        Box(content = content)
    }
}
