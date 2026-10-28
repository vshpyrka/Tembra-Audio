package audio.tembra.ui.timeline

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.recording.MonitorState
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.MonitorToggle
import audio.tembra.ui.timeline.components.NEEDS_HEADPHONES
import kotlin.test.Test
import kotlin.test.assertEquals

/** The monitoring toggle (feature 010, spec FR-016, FR-017; T067): the three states as a switch, the suspended state's name, the tap. */
@OptIn(ExperimentalTestApi::class)
class MonitorToggleTest {

    private var taps = 0

    @Test
    fun offIsAnUncheckedSwitchNamedMonitorInputAndATapReports() = runComposeUiTest {
        setContent { TembraTheme { MonitorToggle(state = MonitorState.Off, onToggle = { taps++ }) } }
        onNodeWithTag(TimelineTestTags.MONITOR).assertContentDescriptionEquals("Monitor input").assertIsOff().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun onIsChecked() = runComposeUiTest {
        setContent { TembraTheme { MonitorToggle(state = MonitorState.On, onToggle = { taps++ }) } }
        onNodeWithTag(TimelineTestTags.MONITOR).assertIsOn()
    }

    @Test
    fun suspendedOnTheSpeakerReadsNeedsHeadphonesAndATapStillReports() = runComposeUiTest {
        setContent { TembraTheme { MonitorToggle(state = MonitorState.SuspendedSpeaker, onToggle = { taps++ }) } }
        onNodeWithTag(TimelineTestTags.MONITOR)
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, NEEDS_HEADPHONES))
            .performClick()
        assertEquals(1, taps)
    }

    @Test
    fun disabledWithoutAProject() = runComposeUiTest {
        setContent { TembraTheme { MonitorToggle(state = MonitorState.Off, onToggle = { taps++ }, enabled = false) } }
        onNodeWithTag(TimelineTestTags.MONITOR).assertIsNotEnabled()
    }
}
