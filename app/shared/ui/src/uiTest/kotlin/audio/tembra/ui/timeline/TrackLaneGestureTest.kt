package audio.tembra.ui.timeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.TrackLane
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/** The lane's viewport gestures (spec FR-017, FR-018; clarification 3; T099, US3): pinch, plain drag, wheel, modifier + wheel. */
@OptIn(ExperimentalTestApi::class)
class TrackLaneGestureTest {

    private val track = TrackUi(
        id = TrackId("t1"), name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(FakeOverviews.clip("c1", "drums", 0.0, 4.0)),
    )
    private val viewport = TimelineViewport(pixelsPerBeat = 20.0, scrollBeats = Beats.Zero, widthPx = 320.0, densityScale = 1.0, projectLengthBeats = Beats(16.0))
    private val zooms = mutableListOf<Pair<Double, Double>>()
    private val scrolls = mutableListOf<Double>()
    private val taps = mutableListOf<String>()

    private val moves = mutableListOf<String>()

    private fun ComposeUiTest.setLane() {
        setContent {
            TembraTheme {
                TrackLane(
                    track = track, index = 0, firstClipIndex = 0, viewport = viewport, selection = Selection.None,
                    signature = TimeSignature.FourFour, height = 88.dp,
                    onSelectClip = { taps += "select" }, onClearSelection = { taps += "clear" },
                    onZoomAround = { x, factor -> zooms += x to factor }, onScrollBy = { scrolls += it },
                    rowPitch = 144.dp, canSplit = true,
                    onClipDragStart = { moves += "start" }, onClipDrag = { _, beats, tracks -> moves += "drag(${(beats * 10).roundToInt() / 10.0},$tracks)" },
                    onClipDragEnd = { moves += "end" }, onClipDragCancel = { moves += "cancel" },
                    onSplit = { moves += "split" }, onDeleteClip = { moves += "delete" },
                )
            }
        }
    }

    // --- Clips (T104, US4) -----------------------------------------------------------------------------------------

    @Test
    fun aTapOnAClipSelectsIt() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.clip(0)).performClick()
        assertEquals(listOf("select"), taps)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun aPlainDragOnAClipScrollsAndTheClipStays() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.clip(0)).performTouchInput { swipeLeft(startX = 70f, endX = 10f) }
        assertTrue(scrolls.isNotEmpty(), "the swipe scrolled the lane")
        assertTrue(moves.isEmpty(), "no lift without a long press: $moves")
    }

    @Test
    fun aLongPressLiftsAndTheDragMovesUntilRelease() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.clip(0)).performTouchInput {
            down(Offset(40f, 44f))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 0f))
            moveTo(Offset(80f, 44f))
            moveTo(Offset(120f, 44f + 144f))
            up()
        }
        assertEquals("start", moves.first(), "the long press lifted the clip: $moves")
        assertEquals("end", moves.last())
        assertTrue(moves.any { it.startsWith("drag(") }, "the drag reported positions: $moves")
        assertEquals("drag(4.0,1)", moves[moves.lastIndex - 1], "80 px at 20 px per beat and one row down")
        assertTrue(scrolls.isEmpty(), "a lifted clip does not scroll the lane")
    }

    @Test
    fun aSecondaryClickOpensTheClipMenu() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.clip(0)).performMouseInput { rightClick(Offset(40f, 44f)) }
        onNodeWithTag("${TimelineTestTags.clip(0)}.menu.delete").assertExists().performClick()
        assertEquals(listOf("delete"), moves)
    }

    @Test
    fun aPinchZoomsAroundItsCentroid() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.lane(0)).performTouchInput {
            pinch(start0 = Offset(140f, 44f), end0 = Offset(60f, 44f), start1 = Offset(180f, 44f), end1 = Offset(260f, 44f))
        }
        assertTrue(zooms.isNotEmpty(), "no zoom reported")
        val factor = zooms.fold(1.0) { acc, (_, f) -> acc * f }
        assertTrue(factor > 2.0, "the fingers spread four times apart, zoom was ×$factor")
        assertTrue(zooms.all { (x, _) -> x in 120.0..200.0 }, "the centroid stayed near the middle: ${zooms.map { it.first }}")
        assertTrue(taps.isEmpty())
    }

    @Test
    fun aPlainDragScrollsWithTheContent() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.lane(0)).performTouchInput { swipeLeft(startX = 250f, endX = 50f) }
        assertTrue(scrolls.isNotEmpty(), "no scroll reported")
        assertTrue(scrolls.sum() > 100.0, "a leftward swipe scrolls the view forward: ${scrolls.sum()}")
        assertTrue(zooms.isEmpty())
        assertTrue(taps.isEmpty())
    }

    @Test
    fun aTapIsNotAGesture() = runComposeUiTest {
        setLane()
        onNodeWithTag(TimelineTestTags.lane(0)).performClick()
        assertEquals(listOf("clear"), taps)
        assertTrue(scrolls.isEmpty() && zooms.isEmpty())
    }

    @Test
    fun theWheelScrollsAndWithControlZooms() = runComposeUiTest {
        setLane()
        val lane = onNodeWithTag(TimelineTestTags.lane(0))
        lane.performMouseInput {
            moveTo(Offset(160f, 44f))
            scroll(1f)
        }
        assertEquals(1, scrolls.size, "one notch, one scroll")
        assertTrue(zooms.isEmpty())
        lane.performKeyInput { keyDown(Key.CtrlLeft) }
        lane.performMouseInput { scroll(-1f) }
        lane.performKeyInput { keyUp(Key.CtrlLeft) }
        assertEquals(1, zooms.size, "a notch with Control zooms")
        assertTrue(zooms.single().second > 1.0, "scrolling away from the user zooms in")
        assertEquals(1, scrolls.size)
    }
}
