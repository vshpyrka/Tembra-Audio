package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme

/**
 * The compact-width navigation bar (spec FR-003, FR-004): Material 3 Expressive's [ShortNavigationBar] on
 * surfaceContainerLowest, its height and item metrics from Material's tokens, one icon-only item per destination. The
 * label is the glyph's description and its hover tooltip ([NavigationItemIcon]), so every item is still announced as a
 * named tab (spec FR-014, FR-015). The active item's indicator is primaryContainer with onPrimaryContainer content; it
 * moves with the theme's expressive motion. Keyboard focus draws Material's inset focus ring inside the indicator
 * ([InsetFocusRing]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TembraNavigationBar(
    items: List<NavigationItemSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedIndicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
    )
    CompositionLocalProvider(LocalRippleThemeConfiguration provides InsetFocusRing) {
        ShortNavigationBar(
            modifier = modifier.testTag("chrome.bottomBar"),
            containerColor = colors.surfaceContainerLowest,
            // The sides as the content clears them: the iPhone Duo's closed screen keeps its status column at the end edge.
            windowInsets = WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            for (item in items) key(item.id) {
                val source = item.interactionSource ?: remember { MutableInteractionSource() }
                ShortNavigationBarItem(
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    icon = { NavigationItemIcon(item, source, TooltipAnchorPosition.Above) },
                    label = null,
                    modifier = Modifier.testTag("nav.item.${item.id}"),
                    colors = itemColors,
                    interactionSource = source,
                )
            }
        }
    }
}
