package audio.tembra.ui.timeline

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TAG
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every control reads (spec FR-058; T123): the track, the clip, the playhead, the readout and the master volume, and no meter exists. */
@OptIn(ExperimentalTestApi::class)
class TimelineSemanticsTest {

    private val state = TimelineUiState(
        loaded = true, projectName = "Demo project", tempo = Tempo(124), timeSignature = TimeSignature.FourFour,
        tracks = persistentListOf(
            TrackUi(
                id = TrackId("t1"), name = "Guitar", label = InstrumentLabel.Guitar, colorIndex = 1, gainDb = GainDb(-2.4f), muted = true, soloed = false, audible = false,
                clips = persistentListOf(FakeOverviews.clip("c1", "drums", 0.0, 64.0)),
            ),
        ),
        lengthBeats = Beats(64.0), lengthSeconds = 64.0 * 60.0 / 124.0, positionBeats = Beats(4.0), positionSeconds = 4.0 * 60.0 / 124.0,
        viewport = TimelineViewport.fitted(800.0, 1.0, Beats(64.0), 4), isFirstContentReady = true,
    )

    @Test
    fun theTrackTheClipThePlayheadTheReadoutAndTheMasterVolumeRead() = runComposeUiTest {
        setContent { TembraTheme { TimelineScreenContent(state, TimelineCallbacks.None, ScriptedTransportTelemetry.stopped(0.0), TimelineLayout.Compact, SnackbarHostState()) } }
        onNodeWithTag(TimelineTestTags.header(0)).assertContentDescriptionEquals("Guitar, Guitar, −2.4dB, muted").assertHasClickAction()
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("drums, track 1, bar 1 beat 1, 31 seconds").assertHasClickAction()
        onNodeWithTag(TimelineTestTags.PLAYHEAD).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2.1"))
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:01.00 of 00:30.00")
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals("Master volume")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 99)))
        onNodeWithTag(TimelineTestTags.mute(0)).assertContentDescriptionEquals("Mute")
    }

    @Test
    fun everyControlCarriesItsTagAndNoMeterExists() = runComposeUiTest {
        setContent { TembraTheme { TimelineScreenContent(state, TimelineCallbacks.None, ScriptedTransportTelemetry.stopped(0.0), TimelineLayout.Compact, SnackbarHostState()) } }
        for (tag in listOf(
            TimelineTestTags.ROOT, TimelineTestTags.TOOLBAR, PerfAnchors.TIMELINE_ADD_TRACK, PerfAnchors.TIMELINE_SPLIT, PerfAnchors.TIMELINE_UNDO,
            TimelineTestTags.REDO, "${TimelineTestTags.OVERFLOW}.button", PerfAnchors.TIMELINE_RULER, TimelineTestTags.TEMPO_PILL, TimelineTestTags.SIGNATURE_PILL,
            TimelineTestTags.TRACK_LIST, TimelineTestTags.lane(0), TimelineTestTags.header(0), TimelineTestTags.mute(0), TimelineTestTags.solo(0),
            PerfAnchors.TIMELINE_TRACK_MENU_FIRST, PerfAnchors.TIMELINE_CLIP_FIRST, TimelineTestTags.PLAYHEAD, PerfAnchors.TRANSPORT_PLAY,
            PerfAnchors.TRANSPORT_SKIP_START, PerfAnchors.TRANSPORT_METRONOME, PerfAnchors.TRANSPORT_READOUT, TAG,
        )) onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        assertEquals(0, onAllNodes(hasTestTag("meter"), useUnmergedTree = true).fetchSemanticsNodes().size, "the timeline carries no level meters (feature 010)")
    }
}
