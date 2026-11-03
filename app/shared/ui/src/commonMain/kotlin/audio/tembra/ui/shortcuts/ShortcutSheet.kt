package audio.tembra.ui.shortcuts

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import audio.tembra.ui.designsystem.ScrollbarAdapter
import audio.tembra.ui.designsystem.tembraScrollbar
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportButton

/** The sheet's tag, its list's, and each group's (`shortcuts.group.<scope>`). */
const val SHORTCUT_SHEET_TAG = "shortcuts.sheet"
const val SHORTCUT_LIST_TAG = "shortcuts.list"

fun shortcutGroupTag(scope: ShortcutScope): String = "shortcuts.group.${scope.name.lowercase()}"

/**
 * Every shortcut the app answers to (feature 011, spec FR-022; contracts/keyboard-shortcuts.md "The shortcut sheet"):
 * the [ShortcutCatalogue] grouped by scope (Everywhere, then the destinations in navigation order), each row the action
 * and its chords as key caps in the platform's modifier. A dialog of its own: Escape, a click outside or the close
 * button closes it through [onDismiss]. The list scrolls, with a scrollbar under a pointer.
 */
@Composable
fun ShortcutSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val primary = LocalShortcutPlatform.current.primary
    val groups = remember { ShortcutScope.entries.map { scope -> scope to ShortcutCatalogue.all.filter { it.scope == scope } } }
    val listState = rememberLazyListState()
    // Focus moves into the sheet, so Escape reaches it on every platform; the window behind keeps its focused control,
    // which has focus again once the sheet closes (contracts/keyboard-shortcuts.md rule 4).
    val sheetFocus = remember { FocusRequester() }
    RegisterOverlay(onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        // Requested from the dialog's own content, which is attached in a window (iOS: a layer) of its own.
        LaunchedEffect(sheetFocus) { sheetFocus.requestFocus() }
        Surface(
            shape = RoundedCornerShape(TembraShapes.extraLarge),
            color = colors.surfaceContainerHigh,
            modifier = modifier
                .widthIn(max = spacing.shortcutSheetMaxWidth)
                .testTag(SHORTCUT_SHEET_TAG)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) true.also { onDismiss() } else false
                }
                .focusRequester(sheetFocus)
                .focusable(),
        ) {
            Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ShortcutCopy.sheetTitle, style = TembraTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                    TransportButton(icon = TembraIcon.Close, description = ShortcutCopy.close, onClick = onDismiss, testTag = "$SHORTCUT_SHEET_TAG.close")
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = spacing.shortcutSheetMaxHeight)
                        .testTag(SHORTCUT_LIST_TAG)
                        .tembraScrollbar(ScrollbarAdapter(listState), Orientation.Vertical),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    items(groups, key = { it.first.name }) { (scope, shortcuts) ->
                        ShortcutSheetGroup(
                            title = ShortcutCopy.scopeTitle(scope),
                            rows = shortcuts.map { shortcut -> shortcut.label to shortcut.chords.map { ShortcutCopy.keyCaps(it, primary) } },
                            modifier = Modifier.testTag(shortcutGroupTag(scope)),
                        )
                    }
                }
            }
        }
    }
}
