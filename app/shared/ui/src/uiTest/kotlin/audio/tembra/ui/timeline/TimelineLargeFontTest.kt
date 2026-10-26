package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
import audio.tembra.ui.timeline.components.TempoPill
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Font scale 2.0 on a 360 dp phone (spec FR-055, FR-059; T123; D1 T141): the readout stays intact, the chips still read,
 * three lanes and the ruler stay visible with either zoom control, and the toolbar's tempo pill keeps its whole width
 * beside a 44 dp overflow button.
 */
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

    /**
     * The 360 × 780 dp screen at font scale 2 with [zoomControl], and a reference tempo pill measured with no width limit
     * and placed left of the window, where it covers nothing: what the toolbar's pill must match to be whole.
     */
    private fun ComposeUiTest.setScreen(zoomControl: ZoomControl) {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                TembraTheme {
                    Box(Modifier.requiredWidth(360.dp).height(780.dp)) {
                        TimelineScreenContent(state.copy(zoomControl = zoomControl), TimelineCallbacks.None, ScriptedTransportTelemetry.stopped(0.0), TimelineLayout.Compact, SnackbarHostState())
                        Box(
                            Modifier.layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints())
                                layout(0, 0) { placeable.place(-placeable.width, 0) }
                            },
                        ) {
                            TempoPill(tempo = state.tempo, onClick = {}, testTag = REFERENCE_PILL)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun atTwiceTheFontSizeTheTimelineKeepsItsShape() = runComposeUiTest {
        setScreen(ZoomControl.Buttons)
        assertTheShape()
        onNodeWithTag(TimelineTestTags.ZOOM_IN).assertIsDisplayed().assertWidthIsEqualTo(44.dp)
    }

    @Test
    fun atTwiceTheFontSizeTheStripLeavesThreeTracksAndTheRuler() = runComposeUiTest {
        setScreen(ZoomControl.OverviewStrip)
        assertTheShape()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW).assertIsDisplayed().getUnclippedBoundsInRoot()
        val play = onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).getUnclippedBoundsInRoot()
        assertTrue(strip.bottom <= play.top, "the strip $strip runs into the transport bar $play")
    }

    private fun ComposeUiTest.assertTheShape() {
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertIsDisplayed().assertContentDescriptionEquals("00:00.00 of 00:30.00")
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertIsDisplayed()
        onNodeWithTag(PerfAnchors.TIMELINE_RULER).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.header(0)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(0)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(1)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.lane(2)).assertIsDisplayed()
        onNodeWithTag(TimelineTestTags.clip(0)).assertContentDescriptionEquals("loop, track 1, bar 1 beat 1, 8 seconds")
        // The toolbar's tempo pill is as wide as the unconstrained reference (nothing of it is cut), and the overflow
        // button after it keeps its 44 dp inside the 360 dp window.
        val reference = onNodeWithTag(REFERENCE_PILL).getUnclippedBoundsInRoot()
        val pill = onNodeWithTag(TimelineTestTags.TOOLBAR_TEMPO).assertIsDisplayed().getUnclippedBoundsInRoot()
        val referenceWidth = reference.right - reference.left
        val pillWidth = pill.right - pill.left
        assertTrue(abs((pillWidth - referenceWidth).value) < 0.5f, "the tempo pill is $pillWidth wide, its text needs $referenceWidth")
        val overflow = onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).assertIsDisplayed().assertWidthIsEqualTo(44.dp).getUnclippedBoundsInRoot()
        val window = onNodeWithTag(TimelineTestTags.ROOT).getUnclippedBoundsInRoot()
        assertTrue(pill.right <= overflow.left && overflow.right <= window.right, "the pill $pill, the overflow $overflow, the window $window")
    }

    private companion object {
        const val REFERENCE_PILL = "test.referenceTempoPill"
    }
}
