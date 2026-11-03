package audio.tembra.ui.timeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTrackpadInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.TrackLane
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android trackpads (feature 011, spec FR-014, research R9): a two-finger pinch arrives as `Scale` pointer events and a
 * two-finger swipe as `Pan` events, which the touch gesture detectors ignore; the timeline zooms around the pointer on
 * the first and scrolls on the second. Android only: Compose Multiplatform's iOS test API injects no trackpad gestures
 * (the iPad's pinch arrives through the shell's bridge, `TrackpadPinchTest`).
 */
@OptIn(ExperimentalTestApi::class)
class TrackpadGesturesTest {

    private val zooms = mutableListOf<Pair<Double, Double>>()
    private val scrolls = mutableListOf<Double>()

    private fun androidx.compose.ui.test.ComposeUiTest.setLane() {
        setContent {
            TembraTheme {
                TrackLane(
                    track = TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf()),
                    index = 0, firstClipIndex = 0,
                    viewport = TimelineViewport(pixelsPerBeat = 20.0, scrollBeats = Beats.Zero, widthPx = 320.0, densityScale = 1.0, projectLengthBeats = Beats(16.0)),
                    selection = Selection.None, signature = TimeSignature.FourFour, height = 88.dp,
                    onSelectClip = {}, onClearSelection = {},
                    onZoomAround = { x, factor -> zooms += x to factor }, onScrollBy = { scrolls += it },
                )
            }
        }
    }

    @Test
    fun aTrackpadPinchZoomsAroundThePointer() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.lane(0)).performTrackpadInput {
            moveTo(Offset(120f, 40f))
            scaleStart()
            scaleChangeBy(1.5f)
            scaleChangeBy(1.5f)
            scaleEnd()
        }
        assertTrue(zooms.isNotEmpty(), "the pinch did not zoom")
        assertTrue(zooms.fold(1.0) { product, zoom -> product * zoom.second } > 2.0, "the zoom factors: $zooms")
        assertTrue(zooms.all { it.first == 120.0 }, "the zoom is not centred on the pointer: $zooms")
    }

    @Test
    fun aTwoFingerSwipeScrollsTime() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.lane(0)).performTrackpadInput {
            moveTo(Offset(120f, 40f))
            panStart()
            panMoveBy(Offset(-60f, 0f))
            panEnd()
        }
        assertTrue(scrolls.sum() > 0.0, "a leftward swipe moves later in time: $scrolls")
    }
}
