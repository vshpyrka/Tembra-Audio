package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Font scale 2.0 on a 360 dp phone (spec FR-059; T123): the readout stays intact, the chips still read, three lanes stay visible. */
@OptIn(ExperimentalTestApi::class)
class TimelineLargeFontTest {

    private fun track(index: Int) = TrackUi(
        id = TrackId("t$index"), name = "Track ${index + 1}", label = InstrumentLabel.entries[index], colorIndex = index, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(FakeOverviews.clip("c$index", "loop", 0.0, 16.0)),
    )

    private val state = TimelineUiState(
        loaded = true, projectName = "Demo project", tempo = Tempo(124), timeSignature = TimeSignature.FourFour,
        tracks = persistentListOf(track(0), track(1), track(2), track(3)), lengthBeats = Beats(64.0), lengthSeconds = 30.9677,
        viewport = TimelineViewport.fitted(360.0, 1.0, Beats(64.0), 4), isFirstContentReady = true,
    )

    @Test
    fun atTwiceTheFontSizeTheTimelineKeepsItsShape() = runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                TembraTheme {
                    Box(Modifier.width(360.dp).height(780.dp)) {
                        TimelineScreenContent(state, TimelineCallbacks.None, ScriptedTransportTelemetry.stopped(0.0), TimelineLayout.Compact, SnackbarHostState())
                    }
                }
            }
        }
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertIsDisplayed().assertContentDescriptionEquals("00:00.00 of 00:30.00")
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.TEMPO_PILL).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.header(0)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(0)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(1)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(2)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("loop, track 1, bar 1 beat 1, 8 seconds")
    }
}
