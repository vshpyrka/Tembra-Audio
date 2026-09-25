package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme

/**
 * The side panel frame (spec FR-006, FR-018, FR-019): 320 dp wide on surfaceContainerLow with the design's 12 dp
 * corners and padding, a headlineSmall title above [content]. It has no collapse control by design (FR-012).
 */
@Composable
fun TembraSupportingPane(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier.width(spacing.supportingPaneWidth).fillMaxHeight().testTag("chrome.supportingPane"),
        color = TembraTheme.colors.surfaceContainerLow,
        shape = TembraTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = title, style = TembraTheme.typography.headlineSmall, color = TembraTheme.colors.onSurface)
            content()
        }
    }
}
