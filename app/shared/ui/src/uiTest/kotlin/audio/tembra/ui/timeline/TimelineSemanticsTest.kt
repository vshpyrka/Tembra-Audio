package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.ZoomControl
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.MASTER_BUTTON_TAG
import audio.tembra.ui.transport.MASTER_POPUP_TAG
import audio.tembra.ui.transport.TAG
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every control reads (spec FR-058; T123; D1 T141): the track, the clip, the playhead, the readout and the master
 * volume, every control carries its tag for each zoom control and each open sheet, and no meter exists. The screen is
 * held at a phone's 360 dp, where the transport bar shows the master volume as the speaker button (spec FR-007, D1).
 */
@OptIn(ExperimentalTestApi::class)
class TimelineSemanticsTest {

    private val screen = mutableStateOf(
        TimelineUiState(
            loaded = true, projectName = "Demo project", tempo = Tempo(124), timeSignature = TimeSignature.FourFour,
            tracks = persistentListOf(
                TrackUi(
                    id = TrackId("t1"), name = "Guitar", label = InstrumentLabel.Guitar, colorIndex = 1, gainDb = GainDb(-2.4f), muted = true, soloed = false, audible = false,
                    clips = persistentListOf(FakeOverviews.clip("c1", "drums", 0.0, 64.0)),
                ),
            ),
            lengthBeats = Beats(64.0), lengthSeconds = 64.0 * 60.0 / 124.0, positionBeats = Beats(4.0), positionSeconds = 4.0 * 60.0 / 124.0,
            viewport = TimelineViewport.fitted(800.0, 1.0, Beats(64.0), 4), isFirstContentReady = true,
        ),
    )

    private fun ComposeUiTest.setScreen() {
        setContent {
            TembraTheme {
                Box(Modifier.requiredWidth(PHONE_WIDTH).fillMaxHeight()) {
                    TimelineScreenContent(screen.value, TimelineCallbacks.None, ScriptedTransportTelemetry.stopped(0.0), TimelineLayout.Compact, SnackbarHostState())
                }
            }
        }
    }

    @Test
    fun theTrackTheClipThePlayheadTheReadoutAndTheMasterVolumeRead() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TimelineTestTags.header(0)).assertContentDescriptionEquals("Guitar, Guitar, −2.4dB, muted").assertHasClickAction()
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("drums, track 1, bar 1 beat 1, 31 seconds").assertHasClickAction()
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2.1"))
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:01.00 of 00:30.00")
        onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).assertContentDescriptionEquals("Tempo 124 beats per minute").assertHasClickAction()
        onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).assertContentDescriptionEquals("Zoom 1×, fit the project").assertHasClickAction()
        // At a phone's width the master volume is the speaker button reading the percentage; its pop-up holds the slider.
        onNodeWithTag(TAG).assertDoesNotExist()
        onNodeWithTag(MASTER_BUTTON_TAG)
            .assertContentDescriptionEquals("Master volume")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .performClick()
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals("Master volume")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 99)))
        onNodeWithTag(TimelineTestTags.mute(0)).assertContentDescriptionEquals("Mute")
    }

    @Test
    fun everyControlCarriesItsTagAndNoMeterExists() = runComposeUiTest {
        setScreen()
        val always = listOf(
            TimelineTestTags.ROOT, TimelineTestTags.TOOLBAR, PerfAnchors.TIMELINE_ADD_TRACK, PerfAnchors.TIMELINE_SPLIT, PerfAnchors.TIMELINE_UNDO,
            TimelineTestTags.REDO, TimelineTestTags.TOOLBAR_TEMPO, TimelineTestTags.OVERFLOW_BUTTON, PerfAnchors.TIMELINE_RULER,
            TimelineTestTags.TRACK_LIST, TimelineTestTags.lane(0), TimelineTestTags.header(0), TimelineTestTags.mute(0), TimelineTestTags.solo(0),
            PerfAnchors.TIMELINE_TRACK_MENU_FIRST, PerfAnchors.TIMELINE_CLIP_FIRST, TimelineTestTags.PLAYHEAD, PerfAnchors.TRANSPORT_PLAY,
            PerfAnchors.TRANSPORT_SKIP_START, PerfAnchors.TRANSPORT_METRONOME, PerfAnchors.TRANSPORT_READOUT, MASTER_BUTTON_TAG,
        )
        val buttons = listOf(TimelineTestTags.ZOOM_OUT, TimelineTestTags.ZOOM_LEVEL, TimelineTestTags.ZOOM_IN)
        val strip = listOf(TimelineTestTags.OVERVIEW, TimelineTestTags.OVERVIEW_WINDOW)
        val gone = listOf("timeline.ruler.tempo", "timeline.ruler.signature", "timeline.toolbar.zoom")
        for (tag in always + buttons) onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        for (tag in strip + gone) onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, onAllNodes(hasTestTag("meter"), useUnmergedTree = true).fetchSemanticsNodes().size, "the timeline carries no level meters (feature 010)")

        screen.value = screen.value.copy(zoomControl = ZoomControl.OverviewStrip)
        waitForIdle()
        for (tag in always + strip) onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        for (tag in buttons + gone) onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag(MASTER_BUTTON_TAG).performClick()
        onNodeWithTag(MASTER_POPUP_TAG).assertExists()
        onNodeWithTag(TAG).assertExists()
    }

    @Test
    fun theSheetsCarryTheirTags() = runComposeUiTest {
        setScreen()
        screen.value = screen.value.copy(sheet = TimelineSheet.GridMenu)
        waitForIdle()
        onNodeWithTag(TimelineTestTags.SNAP_SHEET).assertExists()
        onNodeWithTag(TimelineTestTags.snapSheetRow(audio.tembra.core.project.GridSize.Bar)).assertExists()
        screen.value = screen.value.copy(sheet = TimelineSheet.MetronomeSettings)
        waitForIdle()
        onNodeWithTag(TimelineTestTags.SNAP_SHEET).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.METRONOME_SHEET_SIGNATURE).assertExists()
    }

    private companion object {
        val PHONE_WIDTH = 360.dp
    }
}
