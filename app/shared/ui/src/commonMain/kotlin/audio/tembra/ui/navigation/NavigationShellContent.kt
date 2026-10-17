package audio.tembra.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.NavigationItemSpec
import audio.tembra.ui.designsystem.TembraNavigationBar
import audio.tembra.ui.designsystem.TembraNavigationRail
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless frame (contracts/navigation-shell.md): Material 3 adaptive's NavigationSuiteScaffoldLayout places the
 * bar below or the rail at the start according to [layoutMode] (research R4); the content slot hosts the NavDisplay.
 * There is no top bar (removed 2026-10-16: the selected item already names the destination), so the content clears
 * the status bar, a desktop caption and a top cutout itself. UI tests drive this composable with an explicit layout mode.
 */
@Composable
fun NavigationShellContent(
    state: ShellUiState,
    layoutMode: LayoutMode,
    onSelect: (Destination) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember { Destination.entries.map { NavigationItemSpec(it.id, it.label, it.icon) } }
    // Re-selecting the active item changes nothing (spec FR-008), so the callback is not even invoked.
    val select: (String) -> Unit = { id ->
        if (id != state.selected.id) onSelect(Destination.entries.first { it.id == id })
    }
    val bottomBar = layoutMode == LayoutMode.BottomBar
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TembraTheme.colors.background)
            .testTag("nav.shell"),
    ) {
        if (layoutMode == LayoutMode.EndRail) {
            EndRailFrame(items = items, state = state, onSelect = select, onBack = onBack)
            return@Column
        }
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                if (bottomBar) {
                    TembraNavigationBar(items = items, selectedId = state.selected.id, onSelect = select)
                } else {
                    TembraNavigationRail(items = items, selectedId = state.selected.id, onSelect = select)
                }
            },
            navigationSuiteType = if (bottomBar) NavigationSuiteType.ShortNavigationBarCompact else NavigationSuiteType.WideNavigationRailCollapsed,
            state = rememberNavigationSuiteScaffoldState(),
        ) {
            // The content clears the top inset; the bar clears the bottom inset itself; beside a rail the content reaches
            // the bottom and end edges.
            val sides = if (bottomBar) WindowInsetsSides.Horizontal else WindowInsetsSides.End + WindowInsetsSides.Bottom
            val contentInsets = WindowInsetsSides.Top + sides
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(contentInsets))) {
                TembraNavDisplay(backStack = state.backStack, layoutMode = layoutMode, onBack = onBack, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The iPhone Duo experiment's frame ([LayoutMode.EndRail]): the content clears the top, start and bottom insets, and the rail
 * fills the column iOS reserves at the end edge for the status bar and camera (at least Material's own rail width), its
 * items at the bottom of that column.
 */
@Composable
private fun EndRailFrame(items: List<NavigationItemSpec>, state: ShellUiState, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val endInset = WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom)),
        ) {
            TembraNavDisplay(backStack = state.backStack, layoutMode = LayoutMode.EndRail, onBack = onBack, modifier = Modifier.fillMaxSize())
        }
        TembraNavigationRail(
            items = items,
            selectedId = state.selected.id,
            onSelect = onSelect,
            modifier = Modifier.widthIn(min = endInset),
            itemsAlignment = Alignment.Bottom,
        )
    }
}
