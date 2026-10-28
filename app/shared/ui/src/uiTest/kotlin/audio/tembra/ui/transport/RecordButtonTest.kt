package audio.tembra.ui.transport

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Record control's semantics for each state, its target size and its stillness under reduce motion (spec FR-007, FR-029). */
@OptIn(ExperimentalTestApi::class)
class RecordButtonTest {

    private var taps = 0

    private fun ComposeUiTest.setButton(
        state: RecordButtonState,
        beats: Int = -1,
        enabled: Boolean = true,
        reduceMotion: Boolean = false,
    ) {
        setContent {
            val motion = if (reduceMotion) TembraMotion(reduceMotion = true) else LocalTembraMotion.current
            CompositionLocalProvider(LocalTembraMotion provides motion) {
                TembraTheme {
                    RecordButton(state = state, onRecord = { taps++ }, countInBeatsLeft = beats, enabled = enabled)
                }
            }
        }
        mainClock.autoAdvance = false
    }

    @Test
    fun hiddenComposesNothing() = runComposeUiTest {
        setButton(RecordButtonState.Hidden)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertDoesNotExist()
    }

    @Test
    fun idleIsNamedRecordAndForwardsTheClick() = runComposeUiTest {
        setButton(RecordButtonState.Idle)
        mainClock.autoAdvance = true
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals(TransportCopy.record).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun theCountInNamesTheBeatsLeft() = runComposeUiTest {
        setButton(RecordButtonState.CountIn, beats = 3)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 3 beats left")
    }

    @Test
    fun theLastCountInBeatIsSingular() = runComposeUiTest {
        setButton(RecordButtonState.CountIn, beats = 1)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 1 beat left")
    }

    @Test
    fun recordingIsNamedRecording() = runComposeUiTest {
        setButton(RecordButtonState.Recording)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals(TransportCopy.recording)
    }

    @Test
    fun disabledIsNotEnabled() = runComposeUiTest {
        setButton(RecordButtonState.Idle, enabled = false)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertIsNotEnabled()
    }

    @Test
    fun theTargetIs44dp() = runComposeUiTest {
        setButton(RecordButtonState.Idle)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun underReduceMotionTheCountInStaysStableAcrossBeats() = runComposeUiTest {
        var beats by mutableStateOf(3)
        setContent {
            CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = true)) {
                TembraTheme { RecordButton(state = RecordButtonState.CountIn, onRecord = {}, countInBeatsLeft = beats) }
            }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(500)
        beats = 2
        mainClock.advanceTimeBy(500)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 2 beats left")
    }
}
