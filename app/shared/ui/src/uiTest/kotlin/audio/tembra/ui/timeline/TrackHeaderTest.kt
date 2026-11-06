package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.MENU_MOVE_DOWN
import audio.tembra.ui.timeline.components.MENU_MOVE_UP
import audio.tembra.ui.timeline.components.MENU_SETTINGS
import audio.tembra.ui.timeline.components.TrackHeader
import audio.tembra.ui.timeline.components.TrackHeaderCompact
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The two track headers (contracts/timeline-screen.md; T092): semantics, the M/S toggles, the menu's availability. */
@OptIn(ExperimentalTestApi::class)
class TrackHeaderTest {

    private val track = TrackUi(
        id = TrackId("t2"), name = "Track 2", label = InstrumentLabel.Guitar, colorIndex = 1, gainDb = GainDb(-2.4f),
        muted = true, soloed = false, audible = false, clips = persistentListOf(),
    )
    private val calls = mutableListOf<String>()

    /** The open menu as the row that owns it holds it (feature 011: hoisted above the two header forms). */
    private var menuOpen by mutableStateOf(false)

    private fun ComposeUiTest.setCompact(isFirst: Boolean = true, isLast: Boolean = false, item: TrackUi = track, narrow: Boolean = false) {
        setContent {
            TembraTheme {
                TrackHeaderCompact(
                    modifier = if (narrow) Modifier.requiredWidth(360.dp) else Modifier,
                    onArm = { calls += "arm($it)" },
                    track = item, index = 1, selected = true, isFirst = isFirst, isLast = isLast,
                    onSelect = { calls += "select" }, onMute = { calls += "mute($it)" }, onSolo = { calls += "solo($it)" },
                    onSettings = { calls += "settings" }, onAddAudio = { calls += "addAudio" }, onMoveUp = { calls += "up" },
                    onMoveDown = { calls += "down" }, onDelete = { calls += "delete" },
                    menuOpen = menuOpen, onMenuOpen = { menuOpen = it },
                )
            }
        }
    }

    private fun ComposeUiTest.setExpanded(item: TrackUi = track, inputTelemetry: InputTelemetry? = null) {
        setContent {
            TembraTheme {
                TrackHeader(
                    onArm = { calls += "arm($it)" },
                    inputTelemetry = inputTelemetry,
                    track = item, index = 1, selected = false, expanded = false, isFirst = false, isLast = true,
                    onSelect = { calls += "select" }, onMute = { calls += "mute($it)" }, onSolo = { calls += "solo($it)" },
                    onGain = { calls += "gain(${it.readout})" }, onExpand = { calls += "expand($it)" },
                    onSettings = { calls += "settings" }, onAddAudio = { calls += "addAudio" }, onMoveUp = { calls += "up" },
                    onMoveDown = { calls += "down" }, onDelete = { calls += "delete" },
                    menuOpen = menuOpen, onMenuOpen = { menuOpen = it },
                    modifier = Modifier.height(96.dp),
                )
            }
        }
    }

    @Test
    fun compactHeaderDescribesTheTrackAndIs56dp() = runComposeUiTest {
        setCompact()
        onNodeWithTag(TimelineTestTags.header(1))
            .assertContentDescriptionEquals("Track 2, Guitar, −2.4dB, muted")
            .assertHeightIsEqualTo(56.dp)
            .assertIsSelected()
        onNodeWithTag(TimelineTestTags.mute(1)).assertIsOn().assertContentDescriptionEquals("Mute")
        onNodeWithTag(TimelineTestTags.solo(1)).assertIsOff().assertContentDescriptionEquals("Solo")
    }

    @Test
    fun togglesAndTheRowReportTheirIntents() = runComposeUiTest {
        setCompact()
        onNodeWithTag(TimelineTestTags.mute(1)).performClick()
        onNodeWithTag(TimelineTestTags.solo(1)).performClick()
        // The centre of a narrow row lands on the buttons; the name area is at the left.
        onNodeWithTag(TimelineTestTags.header(1)).performTouchInput { click(Offset(24f, centerY)) }
        assertEquals(listOf("mute(false)", "solo(true)", "select"), calls)
    }

    @Test
    fun theMenuListsItsEntriesWithMoveUpUnavailableOnTheFirstTrack() = runComposeUiTest {
        setCompact(isFirst = true, isLast = false)
        onNodeWithTag(TimelineTestTags.trackMenu(1)).performClick()
        onNodeWithTag("${TimelineTestTags.TRACK_MENU}.$MENU_MOVE_UP").assertIsNotEnabled()
        onNodeWithTag("${TimelineTestTags.TRACK_MENU}.$MENU_MOVE_DOWN").assertIsEnabled()
        onNodeWithTag("${TimelineTestTags.TRACK_MENU}.$MENU_SETTINGS").performClick()
        assertEquals(listOf("settings"), calls)
    }

    /** Feature 012 (spec FR-007, FR-008; T068): the expanded header's second row reads the pan under the gain. */
    @Test
    fun expandedHeaderReadsThePanUnderTheGain() = runComposeUiTest {
        setExpanded(item = track.copy(pan = Pan(-35)))
        onNodeWithTag(TimelineTestTags.header(1)).assertContentDescriptionEquals("Track 2, Guitar, −2.4dB, L 35, muted")
        onNodeWithTag(TimelineTestTags.gain(1)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−2.4dB, L 35"))
        onNodeWithText("L 35", useUnmergedTree = true).assertExists()
        onNodeWithTag(TimelineTestTags.gain(1)).assertWidthIsAtLeast(56.dp).assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun expandedHeaderIs200dpWithGainAndChevron() = runComposeUiTest {
        setExpanded()
        onNodeWithTag(TimelineTestTags.header(1)).assertWidthIsEqualTo(200.dp).assertContentDescriptionEquals("Track 2, Guitar, −2.4dB, muted")
        // The readout keeps the room its text needs beside the toggles and the chevron; the pan reads under it (feature 012,
        // spec FR-007), and the arm toggle sits on the first row, where the chip was.
        onNodeWithTag(TimelineTestTags.gain(1)).assertExists().assertWidthIsAtLeast(56.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−2.4dB, C"))
        val arm = onNodeWithTag(TimelineTestTags.arm(1)).getBoundsInRoot()
        val gain = onNodeWithTag(TimelineTestTags.gain(1)).getBoundsInRoot()
        assertTrue(arm.bottom <= gain.top, "the arm toggle $arm is not on the row above the readout $gain")
        onNodeWithText("Guitar", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.chevron(1)).assertContentDescriptionEquals(TimelineCopy.trackSettings).performClick()
        onNodeWithTag(TimelineTestTags.trackMenu(1)).performClick()
        onNodeWithTag("${TimelineTestTags.TRACK_MENU}.$MENU_MOVE_DOWN").assertIsNotEnabled()
        assertEquals(listOf("expand(true)"), calls)
    }

    @Test
    fun armedCompactRowShowsRecordInPlaceOfTheChipAt360dp() = runComposeUiTest {
        setCompact(item = track.copy(armed = true, recordHere = true), narrow = true)
        onNodeWithText("Guitar", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.record(1)).assertExists().assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        onNodeWithText("Track 2").assertWidthIsAtLeast(72.dp)
        onNodeWithTag(TimelineTestTags.arm(1)).assertExists().assertIsOn()
    }

    @Test
    fun disarmedCompactRowKeepsTheChipAndHasNoRecord() = runComposeUiTest {
        setCompact(narrow = true)
        onNodeWithText("Guitar", useUnmergedTree = true).assertExists()
        onNodeWithTag(TimelineTestTags.record(1)).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.arm(1)).assertIsOff()
    }

    @Test
    fun armedButNotRecordHereKeepsTheChip() = runComposeUiTest {
        setCompact(item = track.copy(armed = true, recordHere = false), narrow = true)
        onNodeWithText("Guitar", useUnmergedTree = true).assertExists()
        onNodeWithTag(TimelineTestTags.record(1)).assertDoesNotExist()
    }

    @Test
    fun compactArmToggleReportsItsIntent() = runComposeUiTest {
        setCompact()
        onNodeWithTag(TimelineTestTags.arm(1)).performClick()
        assertEquals(listOf("arm(true)"), calls)
    }

    @Test
    fun compactDescriptionEndsWithArmedWhenArmed() = runComposeUiTest {
        val armed = track.copy(armed = true, recordHere = true)
        setCompact(item = armed)
        val description = onNodeWithTag(TimelineTestTags.header(1)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description.endsWith("armed"), description)
    }

    /**
     * The 96 dp lane holds two 44 dp rows and the padding with nothing to spare, so a meter placed in the column's flow
     * is measured at 0 dp and never drawn (the iPad and Pixel Tablet captures of 2026-11-02): it must lie along the
     * header's bottom edge like the compact header's, visible and full height.
     */
    @Test
    fun expandedArmedHeaderShowsTheMeterAlongItsBottomEdge() = runComposeUiTest {
        setExpanded(item = track.copy(armed = true), inputTelemetry = InputTelemetry { _, _ -> })
        val header = onNodeWithTag(TimelineTestTags.header(1)).assertHeightIsEqualTo(96.dp).getBoundsInRoot()
        val meter = onNodeWithTag(TimelineTestTags.meter(1)).assertIsDisplayed().assertHeightIsEqualTo(4.dp).getBoundsInRoot()
        val gap = (header.bottom - meter.bottom).value
        assertTrue(gap in -0.5f..0.5f, "the meter's bottom is ${gap}dp above the header's bottom edge")
    }

    @Test
    fun expandedHeaderHasA36dpArmToggleThatReports() = runComposeUiTest {
        setExpanded()
        onNodeWithTag(TimelineTestTags.arm(1)).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp).assertIsOff().performClick()
        assertEquals(listOf("arm(true)"), calls)
    }
}
