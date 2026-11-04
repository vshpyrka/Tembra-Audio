package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme

/**
 * The navigation rail for windows from 600 dp (spec FR-003, FR-005): Material 3 Expressive's [WideNavigationRail],
 * collapsed, on surfaceContainerLowest, filling the window's height (the shell has no top bar) with its items clear of
 * the status bar, a desktop caption, a start-side cutout and the gesture area. Width and item metrics come from
 * Material's tokens; each item is icon-only with the label as the glyph's description and hover tooltip
 * ([NavigationItemIcon]), so it is still announced as a named tab (spec FR-014, FR-015); keyboard focus draws
 * Material's inset focus ring inside the indicator ([InsetFocusRing]). Shorter windows scroll the items.
 * [itemsAlignment] places the items in the rail's height: at the top by default, at the bottom in the iPhone Duo
 * experiment's end-edge rail, below the status bar and camera that share its column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TembraNavigationRail(
    items: List<NavigationItemSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemsAlignment: Alignment.Vertical = Alignment.Top,
) {
    CompositionLocalProvider(LocalRippleThemeConfiguration provides InsetFocusRing) {
        WideNavigationRail(
            modifier = modifier.testTag("chrome.rail"),
            colors = WideNavigationRailDefaults.colors(containerColor = TembraTheme.colors.surfaceContainerLowest),
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            arrangement = if (itemsAlignment == Alignment.Bottom) Arrangement.Bottom else Arrangement.Top,
        ) {
            // The rail does not scroll by itself: five items under its top space overflow a 360 dp phone in landscape.
            // The rail places its content at its start edge: the column takes the rail's width and centres the items,
            // which on iOS (the JetBrains port) are only as wide as their indicator.
            Column(
                modifier = Modifier.fillMaxWidth().verticalScrollWithScrollbar(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TembraTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (item in items) key(item.id) {
                    val source = item.interactionSource ?: remember { MutableInteractionSource() }
                    WideNavigationRailItem(
                        selected = item.id == selectedId,
                        onClick = { onSelect(item.id) },
                        icon = { NavigationItemIcon(item, source, TooltipAnchorPosition.End) },
                        label = null,
                        railExpanded = false,
                        modifier = Modifier.testTag("nav.item.${item.id}").pointerHoverIcon(PointerIcon.Hand),
                        interactionSource = source,
                    )
                }
            }
        }
    }
}
