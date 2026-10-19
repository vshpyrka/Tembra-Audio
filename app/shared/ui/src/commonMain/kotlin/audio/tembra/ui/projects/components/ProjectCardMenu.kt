package audio.tembra.ui.projects.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.theme.TembraIcon

/** The card's menu (spec FR-052): rename, description, key, duplicate, delete, and on the demo "Restore demo project". Rows `<testTag>.<id>`. */
@Composable
internal fun ProjectCardMenu(
    expanded: Boolean,
    isDemo: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDescribe: () -> Unit,
    onKey: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRestoreDemo: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    anchor: @Composable () -> Unit,
) {
    TembraMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        items = buildList {
            add(MenuItemSpec(MENU_RENAME, ProjectsCopy.rename, TembraIcon.Settings))
            add(MenuItemSpec(MENU_DESCRIBE, ProjectsCopy.description, TembraIcon.Tune))
            add(MenuItemSpec(MENU_KEY, ProjectsCopy.key, TembraIcon.MusicNote))
            add(MenuItemSpec(MENU_DUPLICATE, ProjectsCopy.duplicate, TembraIcon.ContentCopy))
            add(MenuItemSpec(MENU_DELETE, ProjectsCopy.delete, TembraIcon.Remove))
            if (isDemo) add(MenuItemSpec(MENU_RESTORE, ProjectsCopy.restoreDemo, TembraIcon.LibraryMusic))
        },
        onSelect = { id ->
            when (id) {
                MENU_RENAME -> onRename()
                MENU_DESCRIBE -> onDescribe()
                MENU_KEY -> onKey()
                MENU_DUPLICATE -> onDuplicate()
                MENU_DELETE -> onDelete()
                MENU_RESTORE -> onRestoreDemo()
            }
        },
        modifier = modifier,
        testTag = testTag,
        anchor = anchor,
    )
}

internal const val MENU_RENAME = "rename"
internal const val MENU_DESCRIBE = "describe"
internal const val MENU_KEY = "key"
internal const val MENU_DUPLICATE = "duplicate"
internal const val MENU_DELETE = "delete"
internal const val MENU_RESTORE = "restoreDemo"
