package audio.tembra.ui.designsystem

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stop-and-leave dialog (spec FR-008a): the agreed copy and the two answers by their tags. */
@OptIn(ExperimentalTestApi::class)
class StopAndLeaveDialogTest {

    private var answer: Boolean? = null

    private fun ComposeUiTest.setDialog() {
        setContent {
            TembraTheme {
                StopAndLeaveDialog(onConfirm = { answer = true }, onCancel = { answer = false })
            }
        }
    }

    @Test
    fun showsTheAgreedTitleAndText() = runComposeUiTest {
        setDialog()
        onNodeWithText(TimelineCopy.stopPlaybackTitle).assertExists()
        onNodeWithText(TimelineCopy.stopPlaybackText).assertExists()
        onNodeWithText(TimelineCopy.stopAndLeave).assertExists()
        onNodeWithText(TimelineCopy.keepPlaying).assertExists()
    }

    @Test
    fun stopAndLeaveAnswersTrue() = runComposeUiTest {
        setDialog()
        onNodeWithTag(PerfAnchors.DIALOG_STOP_AND_LEAVE_CONFIRM).performClick()
        assertEquals(true, answer)
    }

    @Test
    fun keepPlayingAnswersFalse() = runComposeUiTest {
        setDialog()
        onNodeWithTag(CANCEL_TAG).performClick()
        assertEquals(false, answer)
    }

    /** The recording variant (feature 010, FR-022; T071): its copy and its own tags, the answers unchanged. */
    @Test
    fun theRecordingVariantReadsStopRecordingAndAnswersThroughItsOwnTags() = runComposeUiTest {
        setContent {
            TembraTheme {
                StopAndLeaveDialog(onConfirm = { answer = true }, onCancel = { answer = false }, recording = true)
            }
        }
        onNodeWithTag(RECORDING_DIALOG_TAG).assertExists()
        onNodeWithText(TimelineCopy.stopRecordingTitle).assertExists()
        onNodeWithText(TimelineCopy.stopRecordingText).assertExists()
        onNodeWithText(TimelineCopy.stopRecordingAndLeave).assertExists()
        onNodeWithText(TimelineCopy.keepRecording).assertExists()
        onNodeWithTag(DIALOG_TAG).assertDoesNotExist()
        onNodeWithTag(RECORDING_CANCEL_TAG).performClick()
        assertEquals(false, answer)
        onNodeWithTag(PerfAnchors.DIALOG_STOP_RECORDING_CONFIRM).performClick()
        assertEquals(true, answer)
    }
}
