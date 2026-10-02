package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.theme.TembraTheme

/**
 * The frame's top bar (spec FR-007, FR-019): the product name as an overline above the destination title, on
 * surfaceContainerLowest, 56 dp tall below the status and cutout insets. Nothing else may be added here.
 */
@Composable
fun TembraTopBar(title: String, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth().testTag("chrome.topBar"),
        color = TembraTheme.colors.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                // Only the top and the sides: a cutout reported on the bottom edge (a foldable rotated 180°) must not pad the bar.
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth()
                .height(spacing.topBarHeight)
                .padding(horizontal = spacing.pageMargin),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TEMBRA AUDIO",
                style = TembraTheme.typography.labelSmall,
                color = TembraTheme.colors.primary,
                maxLines = 1,
            )
            Text(
                text = title,
                style = TembraTheme.typography.headlineSmall,
                color = TembraTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("chrome.topBar.title"),
            )
        }
    }
}
