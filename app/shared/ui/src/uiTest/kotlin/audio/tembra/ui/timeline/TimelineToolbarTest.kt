package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.recording.MonitorState
import audio.tembra.ui.mixer.MixerCopy
import audio.tembra.ui.mixer.MixerTestTags
import audio.tembra.ui.mixer.MixerUi
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.TimelineToolbar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The toolbar's monitoring entry (feature 010, spec FR-016; T067): the toggle after the scissors from 600 dp, the checked overflow row below. */
@OptIn(ExperimentalTestApi::class)
class TimelineToolbarTest {

    private val calls = mutableListOf<String>()

    private fun toolbar(state: TimelineUiState, layout: TimelineLayout) = runComposeUiTest {
        setContent {
            TembraTheme {
                TimelineToolbar(
                    state = state, layout = layout,
                    onAddTrack = {}, onAddAudio = {}, onAddAudioAsNewTrack = {}, onSplit = {}, onUndo = {}, onRedo = {}, onTempo = {},
                    onGrid = {}, onSnapSheet = {}, onOverviewStripShown = {}, onMetronomeSettings = {},
                    onMonitorToggle = { calls += "monitor" }, onFullScreen = {},
                )
            }
        }
        when (layout) {
            TimelineLayout.Medium, TimelineLayout.Expanded -> {
                val scissors = onNodeWithTag(TimelineTestTags.SPLIT).getUnclippedBoundsInRoot()
                val toggle = onNodeWithTag(TimelineTestTags.MONITOR).getUnclippedBoundsInRoot()
                assertTrue(toggle.left >= scissors.right, "the toggle $toggle is not after the scissors $scissors")
                onNodeWithTag(TimelineTestTags.MONITOR).let { if (state.monitor == MonitorState.On) it.assertIsOn() else it.assertIsOff() }.performClick()
                assertEquals(listOf("monitor"), calls)
                // The overflow of the full row holds no monitor entry (opened last: an open menu would take the taps above).
                onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
                onNodeWithTag(TimelineTestTags.OVERFLOW_MONITOR).assertDoesNotExist()
            }
            TimelineLayout.Compact, TimelineLayout.Landscape -> {
                onNodeWithTag(TimelineTestTags.MONITOR).assertDoesNotExist()
                onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
                val row = onNodeWithTag(TimelineTestTags.OVERFLOW_MONITOR)
                val check = onNodeWithTag("${TimelineTestTags.OVERFLOW_MONITOR}.check", useUnmergedTree = true)
                if (state.monitor == MonitorState.Off) check.assertDoesNotExist() else check.assertExists()
                row.performClick()
                assertEquals(listOf("monitor"), calls)
            }
        }
    }

    @Test
    fun theFullRowHoldsTheToggleAfterTheScissors() = toolbar(TimelineUiState(loaded = true, monitor = MonitorState.Off), TimelineLayout.Expanded)

    @Test
    fun theMediumRowHoldsTheToggleCheckedWhenOn() = toolbar(TimelineUiState(loaded = true, monitor = MonitorState.On), TimelineLayout.Medium)

    @Test
    fun theCompactOverflowHoldsACheckedRow() = toolbar(TimelineUiState(loaded = true, monitor = MonitorState.On), TimelineLayout.Compact)

    @Test
    fun theCompactOverflowRowIsUncheckedWhileOff() = toolbar(TimelineUiState(loaded = true, monitor = MonitorState.Off), TimelineLayout.Compact)

    /**
     * Feature 012 (spec FR-017; contracts/mixer-ui.md): the Mixer button sits in the row at every width, a toggle whose state
     * shows; the compact overflow mirrors it as a checked row, the full row's overflow does not.
     */
    private fun mixer(state: TimelineUiState, layout: TimelineLayout) = runComposeUiTest {
        setContent {
            TembraTheme {
                TimelineToolbar(
                    state = state, layout = layout,
                    onAddTrack = {}, onAddAudio = {}, onAddAudioAsNewTrack = {}, onSplit = {}, onUndo = {}, onRedo = {}, onTempo = {},
                    onGrid = {}, onSnapSheet = {}, onOverviewStripShown = {}, onMetronomeSettings = {}, onFullScreen = {},
                    onMixerToggle = { calls += "mixer" },
                )
            }
        }
        val redo = onNodeWithTag(TimelineTestTags.REDO).getUnclippedBoundsInRoot()
        val button = onNodeWithTag(MixerTestTags.TOOLBAR_BUTTON).assertContentDescriptionEquals(MixerCopy.mixer).getUnclippedBoundsInRoot()
        assertTrue(button.left >= redo.right, "the Mixer button $button is not after redo $redo")
        onNodeWithTag(MixerTestTags.TOOLBAR_BUTTON).performClick()
        assertEquals(listOf("mixer"), calls)
        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        when (layout) {
            TimelineLayout.Medium, TimelineLayout.Expanded -> onNodeWithTag(MixerTestTags.OVERFLOW_BUTTON).assertDoesNotExist()
            TimelineLayout.Compact, TimelineLayout.Landscape -> {
                val check = onNodeWithTag("${MixerTestTags.OVERFLOW_BUTTON}.check", useUnmergedTree = true)
                if (state.mixer.open) check.assertExists() else check.assertDoesNotExist()
                onNodeWithTag(MixerTestTags.OVERFLOW_BUTTON).performClick()
                assertEquals(listOf("mixer", "mixer"), calls)
            }
        }
    }

    @Test
    fun theMixerButtonIsInTheFullRowAndNotItsOverflow() = mixer(TimelineUiState(loaded = true), TimelineLayout.Expanded)

    @Test
    fun theCompactRowAndItsOverflowBothCarryTheMixer() = mixer(TimelineUiState(loaded = true, mixer = MixerUi(open = true)), TimelineLayout.Compact)
}
