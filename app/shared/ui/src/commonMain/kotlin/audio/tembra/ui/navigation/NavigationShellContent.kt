package audio.tembra.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.NavigationItemSpec
import audio.tembra.ui.designsystem.TembraNavigationBar
import audio.tembra.ui.designsystem.TembraNavigationRail
import audio.tembra.ui.designsystem.TembraTopBar
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless frame (contracts/navigation-shell.md): the top bar spans the width above Material 3 adaptive's
 * NavigationSuiteScaffoldLayout, which places the bar below or the rail at the start according to [layoutMode]
 * (research R4); the content slot hosts the NavDisplay. UI tests drive this composable with an explicit layout mode.
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
        TembraTopBar(title = state.selected.title)
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                if (bottomBar) {
                    TembraNavigationBar(items = items, selectedId = state.selected.id, onSelect = select)
                } else {
                    TembraNavigationRail(items = items, selectedId = state.selected.id, onSelect = select)
                }
            },
            navigationSuiteType = if (bottomBar) NavigationSuiteType.NavigationBar else NavigationSuiteType.NavigationRail,
            state = rememberNavigationSuiteScaffoldState(),
        ) {
            // The bar clears the bottom inset itself; beside a rail the content reaches the bottom and end edges.
            val contentInsets = if (bottomBar) WindowInsetsSides.Horizontal else WindowInsetsSides.End + WindowInsetsSides.Bottom
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(contentInsets))) {
                TembraNavDisplay(backStack = state.backStack, layoutMode = layoutMode, onBack = onBack, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
