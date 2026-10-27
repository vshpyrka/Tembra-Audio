package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.DialogAction
import audio.tembra.ui.designsystem.TembraDialog
import audio.tembra.ui.timeline.TimelineCopy

/** "Delete track?" with the track's name (spec FR-026): the deletion is undoable, so the text says so. */
@Composable
internal fun DeleteTrackDialog(trackName: String, onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TembraDialog(
        title = DELETE_TITLE,
        text = "\"$trackName\" and its clips are removed. Undo restores it.",
        confirm = DialogAction(TimelineCopy.deleteTrack, onClick = onConfirm, testTag = DELETE_CONFIRM_TAG),
        dismiss = DialogAction(DELETE_CANCEL, onClick = onCancel, testTag = DELETE_CANCEL_TAG),
        modifier = modifier,
        testTag = DELETE_DIALOG_TAG,
    )
}

internal const val DELETE_TITLE = "Delete track?"
internal const val DELETE_CANCEL = "Cancel"
internal const val DELETE_DIALOG_TAG = "dialog.deleteTrack"
internal const val DELETE_CONFIRM_TAG = "dialog.deleteTrack.confirm"
internal const val DELETE_CANCEL_TAG = "dialog.deleteTrack.cancel"
