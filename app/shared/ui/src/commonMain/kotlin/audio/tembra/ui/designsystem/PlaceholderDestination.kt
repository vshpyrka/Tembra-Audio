package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/**
 * The placeholder form every destination takes in this feature (spec FR-011, FR-013): the title and one sentence
 * from the registry, page margins, then whatever [content] a destination already offers below them (Profile's
 * "Keyboard shortcuts" row, feature 011). Root testTag `screen.<id>`, message testTag `screen.<id>.message`.
 */
@Composable
fun PlaceholderDestination(
    destination: Destination,
    modifier: Modifier = Modifier,
    windowWidth: WindowWidth = WindowWidth.Compact,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val spacing = TembraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.pageMargin, vertical = spacing.lg)
            .testTag("screen.${destination.id}"),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = destination.title,
            style = if (windowWidth.isWide) TembraTheme.typography.headlineLarge else TembraTheme.typographyExtras.headlineLgMobile,
            color = TembraTheme.colors.onSurface,
        )
        Text(
            text = destination.placeholderMessage,
            style = TembraTheme.typography.bodyLarge,
            color = TembraTheme.colors.onSurfaceVariant,
            modifier = Modifier.testTag("screen.${destination.id}.message"),
        )
        content()
    }
}
