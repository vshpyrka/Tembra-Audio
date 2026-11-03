package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.timeline.TimelineCopy

/** A clip's context menu (spec FR-032, FR-033): split at the playhead when it lies inside, and delete. Rows `<testTag>.split` and `<testTag>.delete`. */
@Composable
internal fun ClipMenu(
    expanded: Boolean,
    canSplit: Boolean,
    onDismiss: () -> Unit,
    onSplit: () -> Unit,
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
            MenuItemSpec(CLIP_MENU_SPLIT, TimelineCopy.split, TembraIcon.ContentCut, enabled = canSplit),
            MenuItemSpec(CLIP_MENU_DELETE, TimelineCopy.deleteClip, TembraIcon.Remove),
        ),
        onSelect = { id ->
            when (id) {
                CLIP_MENU_SPLIT -> onSplit()
                CLIP_MENU_DELETE -> onDelete()
            }
        },
        modifier = modifier,
        testTag = testTag,
        offset = offset,
        anchor = anchor,
    )
}

internal const val CLIP_MENU_SPLIT = "split"
internal const val CLIP_MENU_DELETE = "delete"
