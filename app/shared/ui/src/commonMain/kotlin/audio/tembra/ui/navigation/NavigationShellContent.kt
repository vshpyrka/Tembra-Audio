package audio.tembra.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PointerShapes
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.designsystem.NavigationItemSpec
import audio.tembra.ui.designsystem.StopAndLeaveDialog
import audio.tembra.ui.designsystem.TembraNavigationBar
import audio.tembra.ui.designsystem.TembraNavigationRail
import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.DialogRequest
import audio.tembra.ui.perf.traceWindowRelayout
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.LocalOverlayStack
import audio.tembra.ui.shortcuts.LocalTextInputFocus
import audio.tembra.ui.shortcuts.OverlayStack
import audio.tembra.ui.shortcuts.RegisterShortcuts
import audio.tembra.ui.shortcuts.RegisterTextInput
import audio.tembra.ui.shortcuts.ShortcutSheet
import audio.tembra.ui.shortcuts.ShortcutTargets
import audio.tembra.ui.shortcuts.TextInputFocus
import audio.tembra.ui.shortcuts.shellKeyHandling
import audio.tembra.ui.theme.TembraTheme

/**
 * The stateless frame (contracts/navigation-shell.md): Material 3 adaptive's NavigationSuiteScaffoldLayout places the
 * bar below or the rail at the start according to [layoutMode] (research R4); the content slot hosts the NavDisplay.
 * There is no top bar (removed 2026-10-16: the selected item already names the destination), so the content clears
 * the status bar, a desktop caption and a top cutout itself. UI tests drive this composable with an explicit layout mode.
 * [dialog] is the session's pending stop-and-leave question (feature 009, spec FR-008a), rendered above everything;
 * [busy] shows the busy pointer over the window while the session imports (feature 011, FR-015a). The root takes the
 * shell's shortcuts and owns the shortcut sheet, which stays open across a window change (feature 011, FR-017, FR-022).
 */
@Composable
fun NavigationShellContent(
    state: ShellUiState,
    layoutMode: LayoutMode,
    onSelect: (Destination) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    dialog: DialogRequest? = null,
    busy: Boolean = false,
    onOpenTrackSettings: (TrackId) -> Unit = {},
    onCloseTrackSettings: () -> Unit = {},
) {
    val items = remember { Destination.entries.map { NavigationItemSpec(it.id, it.label, it.icon) } }
    // Re-selecting the active item changes nothing (spec FR-008), so the callback is not even invoked.
    val select: (String) -> Unit = { id ->
        if (id != state.selected.id) onSelect(Destination.entries.first { it.id == id })
    }
    val bottomBar = layoutMode == LayoutMode.BottomBar
    var shortcutSheetOpen by rememberSaveable { mutableStateOf(false) }
    val showShortcuts = { shortcutSheetOpen = true }
    val currentSelect by rememberUpdatedState(select)
    val overlays = remember { OverlayStack() }
    val shellShortcuts = remember {
        ShellShortcutTargets(open = { destination -> currentSelect(destination.id) }, showShortcuts = showShortcuts, overlays = overlays)
    }
    val textInput = remember { TextInputFocus() }
    RegisterShortcuts(ShortcutTargets(shell = shellShortcuts))
    RegisterTextInput(textInput)
    CompositionLocalProvider(LocalTextInputFocus provides textInput, LocalOverlayStack provides overlays) {
        when (dialog) {
            is DialogRequest.StopAndLeave -> StopAndLeaveDialog(onConfirm = { dialog.answer(true) }, onCancel = { dialog.answer(false) }, recording = dialog.recording)
            null -> Unit
        }
        if (shortcutSheetOpen) ShortcutSheet(onDismiss = { shortcutSheetOpen = false })
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(TembraTheme.colors.background)
                .testTag("nav.shell")
                // A window change that crosses a layout class is measured (feature 011, contracts/perf-instrumentation.md).
                .traceWindowRelayout()
                // The system's busy pointer over the whole window while the session works (feature 011, spec FR-015a).
                .then(if (busy) Modifier.pointerHoverIcon(PointerShapes.busy, overrideDescendants = true) else Modifier)
                .shellKeyHandling(shellShortcuts, textInput, LocalShortcutPlatform.current),
        ) {
            if (layoutMode == LayoutMode.EndRail) {
                EndRailFrame(items = items, state = state, onSelect = select, onBack = onBack, onOpenTrackSettings = onOpenTrackSettings, onCloseTrackSettings = onCloseTrackSettings, onSelectDestination = onSelect, onShowShortcuts = showShortcuts)
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
                // the bottom and end edges. The padding consumes the safe-drawing insets it applies; the scaffold layout
                // consumes nothing. Beside a rail the system's back-gesture zone at the start lies under the rail (wider than
                // any such zone), so the content consumes it too and nothing inside insets for it again (the overview
                // strip, T153).
                val sides = if (bottomBar) WindowInsetsSides.Horizontal else WindowInsetsSides.End + WindowInsetsSides.Bottom
                val contentInsets = WindowInsetsSides.Top + sides
                val underRail = if (bottomBar) Modifier else Modifier.consumeWindowInsets(WindowInsets.systemGestures.only(WindowInsetsSides.Start))
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(contentInsets)).then(underRail)) {
                    TembraNavDisplay(backStack = state.backStack, layoutMode = layoutMode, onBack = onBack, modifier = Modifier.fillMaxSize(), onOpenTrackSettings = onOpenTrackSettings, nestedTrackSettings = state.trackSettings?.trackId, onCloseTrackSettings = onCloseTrackSettings, onSelectDestination = onSelect, onShowShortcuts = showShortcuts)
                }
            }
        }
    }
}

/**
 * The iPhone Duo experiment's frame ([LayoutMode.EndRail]): the content clears the top, start and bottom insets, and the rail
 * fills the column iOS reserves at the end edge for the status bar and camera (at least Material's own rail width), its
 * items at the bottom of that column. The content consumes the end side's safe-drawing and system-gesture insets, which
 * the rail covers, so nothing inside pads for that column again (the overview strip, T153).
 */
@Composable
private fun EndRailFrame(
    items: List<NavigationItemSpec>,
    state: ShellUiState,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTrackSettings: (TrackId) -> Unit,
    onCloseTrackSettings: () -> Unit,
    onSelectDestination: (Destination) -> Unit,
    onShowShortcuts: () -> Unit,
) {
    val endInset = WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                // The rail beside it fills the end column and is wider than the end's gesture zone.
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End).union(WindowInsets.systemGestures.only(WindowInsetsSides.End))),
        ) {
            TembraNavDisplay(backStack = state.backStack, layoutMode = LayoutMode.EndRail, onBack = onBack, modifier = Modifier.fillMaxSize(), onOpenTrackSettings = onOpenTrackSettings, nestedTrackSettings = state.trackSettings?.trackId, onCloseTrackSettings = onCloseTrackSettings, onSelectDestination = onSelectDestination, onShowShortcuts = onShowShortcuts)
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
