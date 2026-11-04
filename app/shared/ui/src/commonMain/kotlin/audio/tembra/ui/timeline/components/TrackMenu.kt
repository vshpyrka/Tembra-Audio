package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.timeline.TimelineCopy

/**
 * A track header's menu (spec FR-025, FR-026, FR-044; contracts/timeline-screen.md): Track settings, Add audio…,
 * Move up, Move down, Delete track. Move up stays listed but unavailable on the first track, Move down on the last.
 * Row ids are `<testTag>.settings` (the journey's `timeline.track.menu.settings` for the first track), `.addAudio`,
 * `.moveUp`, `.moveDown`, `.delete`.
 */
@Composable
internal fun TrackMenu(
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onAddAudio: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    offset: DpOffset = DpOffset.Zero,
    anchor: @Composable () -> Unit,
) {
    TembraMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        items = listOf(
            MenuItemSpec(MENU_SETTINGS, TimelineCopy.trackSettings, TembraIcon.Settings),
            MenuItemSpec(MENU_ADD_AUDIO, TimelineCopy.addAudio, TembraIcon.AudioFile),
            MenuItemSpec(MENU_MOVE_UP, TimelineCopy.moveUp, TembraIcon.ExpandLess, enabled = !isFirst),
            MenuItemSpec(MENU_MOVE_DOWN, TimelineCopy.moveDown, TembraIcon.ExpandMore, enabled = !isLast),
            MenuItemSpec(MENU_DELETE, TimelineCopy.deleteTrack, TembraIcon.Remove),
        ),
        onSelect = { id ->
            when (id) {
                MENU_SETTINGS -> onSettings()
                MENU_ADD_AUDIO -> onAddAudio()
                MENU_MOVE_UP -> onMoveUp()
                MENU_MOVE_DOWN -> onMoveDown()
                MENU_DELETE -> onDelete()
            }
        },
        modifier = modifier,
        testTag = testTag,
        offset = offset,
        anchor = anchor,
    )
}

internal const val MENU_SETTINGS = "settings"
internal const val MENU_ADD_AUDIO = "addAudio"
internal const val MENU_MOVE_UP = "moveUp"
internal const val MENU_MOVE_DOWN = "moveDown"
internal const val MENU_DELETE = "delete"
