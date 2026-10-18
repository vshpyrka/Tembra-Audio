package audio.tembra.ui.timeline

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.DELETE_CONFIRM_TAG
import audio.tembra.ui.timeline.components.DELETE_DIALOG_TAG
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Timeline content at Compact and Expanded under a frozen clock, semantics before pixels
 * (contracts/timeline-screen.md; T079, User Story 1): the demo's tracks and clips, the chrome, the first-content
 * state, and the delete confirmation.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineScreenTest {

    private val drumsId = TrackId("t1")
    private val padId = TrackId("t2")
    private val calls = mutableListOf<String>()

    private fun demoState(ready: Boolean = true): TimelineUiState {
        val status = if (ready) OverviewStatus.Ready else OverviewStatus.Building(0.2f)
        val drums = TrackUi(
            id = drumsId, name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity, muted = false, soloed = false, audible = true,
            clips = persistentListOf(FakeOverviews.clip("c1", "drums", 0.0, 64.0, status = status, withOverview = ready)),
        )
        val pad = TrackUi(
            id = padId, name = "Pad", label = InstrumentLabel.Synth, colorIndex = 3, gainDb = GainDb(-2.4f), muted = true, soloed = false, audible = false,
            clips = persistentListOf(FakeOverviews.clip("c2", "pad", 0.0, 32.0), FakeOverviews.clip("c3", "pad", 32.0, 32.0)),
        )
        return TimelineUiState(
            loaded = true,
            projectName = "Demo project",
            tempo = Tempo(124),
            timeSignature = TimeSignature.FourFour,
            tracks = persistentListOf(drums, pad),
            lengthBeats = Beats(64.0),
            lengthSeconds = 64.0 * 60.0 / 124.0,
            viewport = TimelineViewport.fitted(widthPx = 800.0, densityScale = 1.0, projectLengthBeats = Beats(64.0), beatsPerBar = 4),
            isFirstContentReady = ready,
        )
    }

    private fun ComposeUiTest.setScreen(state: TimelineUiState, layout: TimelineLayout) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                TimelineScreenContent(
                    state = state,
                    callbacks = TimelineCallbacks.None.copy(
                        onPlayPause = { calls += "play" },
                        onConfirmDeleteTrack = { calls += "deleteConfirmed" },
                        onDismissDeleteTrack = { calls += "deleteDismissed" },
                    ),
                    telemetry = ScriptedTransportTelemetry.stopped(0.0),
                    layout = layout,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    @Test
    fun compactShowsTheDemoWithHeaderStripsAboveTheLanes() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, READY))
        onNodeWithTag(TimelineTestTags.READY_MARKER).assertExists()
        onNodeWithTag(TimelineTestTags.TOOLBAR).assertExists()
        onNodeWithTag(PerfAnchors.TIMELINE_RULER).assertExists()
        onNodeWithTag(TimelineTestTags.TEMPO_PILL).assertExists()
        onNodeWithTag(TimelineTestTags.header(0)).assertContentDescriptionEquals("Drums, Drums, +0.0dB").assertHeightIsEqualTo(56.dp)
        onNodeWithTag(TimelineTestTags.header(1)).assertContentDescriptionEquals("Pad, Synth, −2.4dB, muted")
        onNodeWithTag(TimelineTestTags.gain(0)).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.lane(0)).assertHeightIsEqualTo(88.dp)
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("drums, track 1, bar 1 beat 1, 31 seconds")
        onNodeWithTag(TimelineTestTags.clip(2)).assertContentDescriptionEquals("pad, track 2, bar 9 beat 1, 15 seconds")
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.1"))
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertExists()
        onNodeWithText("00:00.00 / 00:30.96", useUnmergedTree = true).assertExists()
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertIsEnabled().performClick()
        assertEquals(listOf("play"), calls)
    }

    @Test
    fun expandedPinsTheHeaderColumnWithTheGainReadout() = runComposeUiTest {
        setScreen(demoState(), TimelineLayout.Expanded)
        onNodeWithTag(TimelineTestTags.header(0)).assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(96.dp)
        onNodeWithTag(TimelineTestTags.gain(0)).assertExists()
        onNodeWithTag(TimelineTestTags.lane(1)).assertHeightIsEqualTo(96.dp)
        onNodeWithTag(TimelineTestTags.clip(1)).assertExists()
    }

    @Test
    fun firstContentWaitsForTheOverviews() = runComposeUiTest {
        setScreen(demoState(ready = false), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOADING))
        onNodeWithTag(TimelineTestTags.READY_MARKER).assertDoesNotExist()
    }

    @Test
    fun anEmptyProjectShowsTheInvitationAndDisablesNothingOnTheTransport() = runComposeUiTest {
        setScreen(TimelineUiState(loaded = true, isFirstContentReady = true), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.EMPTY).assertExists()
        onNodeWithTag(TimelineTestTags.lane(0)).assertDoesNotExist()
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertExists()
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsEnabled()
    }

    @Test
    fun noProjectDisablesTheTransportAndTheToolbar() = runComposeUiTest {
        setScreen(TimelineUiState(), TimelineLayout.Compact)
        onNodeWithTag(TimelineTestTags.ROOT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOADING))
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsNotEnabled()
        onNodeWithTag(PerfAnchors.TIMELINE_ADD_TRACK).assertIsNotEnabled()
    }

    @Test
    fun aPendingDeleteShowsTheDialogNamingTheTrack() = runComposeUiTest {
        setScreen(demoState().copy(confirmDelete = padId), TimelineLayout.Compact)
        onNodeWithTag(DELETE_DIALOG_TAG).assertExists()
        onNodeWithText("\"Pad\" and its clips are removed. Undo restores it.").assertExists()
        onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(listOf("deleteConfirmed"), calls)
    }

    @Test
    fun playingStateShowsPauseAndTheAudiblePosition() = runComposeUiTest {
        setScreen(demoState().copy(playback = PlaybackState.Playing, positionBeats = Beats(8.0), positionSeconds = 8.0 * 60.0 / 124.0), TimelineLayout.Compact)
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertContentDescriptionEquals("Pause")
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3.1"))
    }
}
