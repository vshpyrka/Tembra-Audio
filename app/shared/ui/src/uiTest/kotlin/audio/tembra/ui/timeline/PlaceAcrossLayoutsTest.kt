package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.navigation.LayoutMode
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The user's place across window changes (feature 011, spec FR-007, SC-003; contracts/window-behaviour.md): scrolled
 * to bar 9, zoomed in, a clip selected and its menu open, the window steps 1200 → 700 → 400 → 1200 dp wide (expanded,
 * medium, compact and back, so the track rows change form twice). After each step the same beat is at the left edge,
 * the zoom and the selection are unchanged, and the menu is still open on that clip. The host keeps the viewport the way
 * the ViewModel does on a new width (`viewportMeasured`: same left-edge beat, same zoom).
 */
@OptIn(ExperimentalTestApi::class)
class PlaceAcrossLayoutsTest {

    private val clip = ClipUi(ClipId("c1"), AudioAssetId("a".repeat(64)), "drums", Beats(33.0), Beats(8.0), 0, 48000, 48000, null, OverviewStatus.Ready, true)
    private val track = TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf(clip))
    private val barNine = Beats(32.0)
    private val zoom = 12.0

    private var width by mutableStateOf(1200.dp)
    private var state by mutableStateOf(
        TimelineUiState(
            loaded = true, tracks = persistentListOf(track), timeSignature = TimeSignature.FourFour, grid = GridSize.Eighth,
            selection = Selection.ClipSelected(track.id, clip.id), lengthBeats = Beats(64.0),
            viewport = TimelineViewport(pixelsPerBeat = zoom, scrollBeats = barNine, widthPx = 0.0, densityScale = 1.0, projectLengthBeats = Beats(64.0)),
        ),
    )

    private fun ComposeUiTest.setTimeline() {
        setContent {
            TembraTheme {
                val snackbar = remember { SnackbarHostState() }
                // Pinned top-left: wider than a phone's window, the host overflows to the right and the clip stays on screen.
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width, 800.dp)) {
                    TimelineScreenContent(
                        state = state,
                        callbacks = TimelineCallbacks.None.copy(
                            onViewportMeasured = { widthPx, density, leadInPx ->
                                val port = state.viewport
                                state = state.copy(viewport = port.copy(widthPx = widthPx, densityScale = density, leadInPx = leadInPx).withZoom(port.zoom))
                            },
                        ),
                        telemetry = ScriptedTransportTelemetry.stopped(0.0),
                        layout = TimelineLayout.from(width, 800.dp, if (width < 600.dp) LayoutMode.BottomBar else LayoutMode.Rail),
                        snackbarHostState = snackbar,
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.assertPlaceKept(step: Dp) {
        waitForIdle()
        assertEquals(barNine, state.viewport.scrollBeats, "left-edge beat at $step")
        assertEquals(zoom, state.viewport.pixelsPerBeat, "zoom at $step")
        onNodeWithTag(TimelineTestTags.clip(0)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("${TimelineTestTags.clip(0)}.menu").assertExists("the clip's menu closed at $step")
    }

    @Test
    fun scrollZoomSelectionAndTheOpenMenuSurviveEveryWidth() = runComposeUiTest {
        setTimeline()
        onNodeWithTag(TimelineTestTags.clip(0)).performMouseInput { rightClick() }
        assertPlaceKept(1200.dp)
        for (step in listOf(700.dp, 400.dp, 1200.dp)) {
            width = step
            assertPlaceKept(step)
        }
    }

    /**
     * The lanes report the lead-in with their width: the minimum beside the expanded layout's header column, which keeps the
     * back-gesture zone off them, and at least that on every other layout.
     */
    @Test
    fun theLanesReportTheirLeadIn() = runComposeUiTest {
        setTimeline()
        val minimum = 24.0 * state.viewport.densityScale
        assertEquals(minimum, state.viewport.leadInPx, 0.5, "beside the header column")
        for (step in listOf(700.dp, 400.dp)) {
            width = step
            waitForIdle()
            assertTrue(state.viewport.leadInPx >= minimum - 0.5, "the lead-in at $step is ${state.viewport.leadInPx} px, under the minimum of $minimum")
        }
    }
}
