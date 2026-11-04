package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.ui.metronome.MetronomeCallbacks
import audio.tembra.ui.metronome.MetronomeLayout
import audio.tembra.ui.metronome.MetronomeScreenContent
import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.metronome.ScriptedBeatTelemetry
import audio.tembra.ui.projects.ProjectsLayout
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineScreenContent
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.timeline.TrackUi
import audio.tembra.ui.tuner.ScriptedTelemetry
import audio.tembra.ui.tuner.TunerCallbacks
import audio.tembra.ui.tuner.TunerLayout
import audio.tembra.ui.tuner.TunerScreenContent
import audio.tembra.ui.tuner.TunerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Very wide windows (feature 011, spec FR-008): at 2560 dp the Projects grid has at most four columns, the tuner's and
 * the metronome's screens keep the width they have in an ordinary expanded window (1200 dp) and sit centred, and the
 * timeline uses the whole width for time.
 */
@OptIn(ExperimentalTestApi::class)
class WideWindowTest {

    private val wide = 2560.dp

    /** [tag]'s bounds relative to the [width]-wide host (the test window may be narrower, and centres the host). */
    private fun ComposeUiTest.boundsAt(width: Dp, tag: String, content: @Composable () -> Unit): DpRect {
        setContent { TembraTheme { Box(Modifier.requiredSize(width, 1000.dp).testTag(HOST)) { content() } } }
        waitForIdle()
        val host = onNodeWithTag(HOST).getUnclippedBoundsInRoot()
        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return DpRect(bounds.left - host.left, bounds.top - host.top, bounds.right - host.left, bounds.bottom - host.top)
    }

    private fun assertSameWidthCentred(ordinary: DpRect, wideBounds: DpRect, what: String) {
        assertTrue(abs((wideBounds.width - ordinary.width).value) <= 1f, "$what grew from ${ordinary.width} to ${wideBounds.width}")
        val centre = (wideBounds.left + wideBounds.right) / 2
        assertTrue(abs((centre - wide / 2).value) <= 1f, "$what is not centred (centre at $centre)")
    }

    private companion object {
        const val HOST = "wideWindow.host"
    }

    @Test
    fun projectsShowsAtMostFourColumns() {
        assertTrue(ProjectsLayout.from(wide).columns <= 4)
    }

    @Test
    fun theTunerKeepsItsExpandedWidthCentred() {
        val tuner: @Composable () -> Unit = {
            TunerScreenContent(state = TunerUiState(loaded = true), callbacks = TunerCallbacks.None, telemetry = ScriptedTelemetry(), layout = TunerLayout.Wide)
        }
        var ordinary = DpRect(0.dp, 0.dp, 0.dp, 0.dp)
        runComposeUiTest { ordinary = boundsAt(1200.dp, "screen.tuner", tuner) }
        runComposeUiTest { assertSameWidthCentred(ordinary, boundsAt(wide, "screen.tuner", tuner), "the tuner") }
    }

    @Test
    fun theMetronomeKeepsItsExpandedWidthCentred() {
        val metronome: @Composable () -> Unit = {
            MetronomeScreenContent(
                state = MetronomeUiState(loaded = true), callbacks = MetronomeCallbacks.None, telemetry = ScriptedBeatTelemetry(),
                layout = MetronomeLayout.Wide, snackbarHostState = remember { SnackbarHostState() },
            )
        }
        var ordinary = DpRect(0.dp, 0.dp, 0.dp, 0.dp)
        runComposeUiTest { ordinary = boundsAt(1200.dp, "screen.metronome", metronome) }
        runComposeUiTest { assertSameWidthCentred(ordinary, boundsAt(wide, "screen.metronome", metronome), "the metronome") }
    }

    @Test
    fun theTimelineUsesTheWholeWidth() = runComposeUiTest {
        val track = TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf())
        val bounds = boundsAt(wide, TimelineTestTags.TRACK_LIST) {
            TimelineScreenContent(
                state = TimelineUiState(loaded = true, tracks = persistentListOf(track), timeSignature = TimeSignature.FourFour),
                callbacks = TimelineCallbacks.None,
                telemetry = ScriptedTransportTelemetry.stopped(0.0),
                layout = TimelineLayout.Expanded,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        assertTrue(bounds.width >= wide - 1.dp, "the track list is ${bounds.width} wide in a $wide window")
    }
}
