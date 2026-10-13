package audio.tembra.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.timeline.TimelineCopy

/**
 * The stop-and-leave question (spec FR-008a): "Stop and leave" ([onConfirm], tagged `dialog.stopAndLeave.confirm` so
 * the stress journey can answer it) and "Keep playing" ([onCancel], `dialog.stopAndLeave.cancel`; also the answer to
 * dismissing the dialog).
 */
@Composable
fun StopAndLeaveDialog(onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TembraDialog(
        title = TimelineCopy.stopPlaybackTitle,
        text = TimelineCopy.stopPlaybackText,
        confirm = DialogAction(TimelineCopy.stopAndLeave, onClick = onConfirm, testTag = PerfAnchors.DIALOG_STOP_AND_LEAVE_CONFIRM),
        dismiss = DialogAction(TimelineCopy.keepPlaying, onClick = onCancel, testTag = CANCEL_TAG),
        modifier = modifier,
        testTag = DIALOG_TAG,
    )
}

const val DIALOG_TAG = "dialog.stopAndLeave"
const val CANCEL_TAG = "dialog.stopAndLeave.cancel"
