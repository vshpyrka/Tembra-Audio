package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.OverviewStrip
import audio.tembra.ui.timeline.components.OverviewStripDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The overview strip (spec FR-015, D1; T140): the whole project at the fit scale with its clips as blocks, the
 * playhead read from the telemetry in draw, and a window over the visible span. A press anywhere on the strip behaves the
 * same: a drag locked to its first direction scrolls (sideways) or zooms about the window's centre (up in, down out, 2x
 * per 60 dp), a tap outside the window recentres it and a double tap animates to the fit. Every test uses 40 bars of 4/4 (160 beats) on a 320 dp strip
 * (2 dp a beat; it must fit the narrowest device window, the 360 dp OPPO, since `width` is coerced into the window) and
 * lanes 320 dp wide at 8 dp a beat: 40 beats in view from beat 20, so the window spans 40 to 120 dp. Positions are
 * written as beats through [dpAtBeat]; those tests set a zero margin and gesture inset so the strip is the whole 320 dp,
 * and two tests check the default inset of the owner's verdict on D1 (T151) and that it counts what an ancestor
 * consumed (T153).
 */
@OptIn(ExperimentalTestApi::class)
class OverviewStripTest {

    private var densityScale = 1f
    private var playhead: Color = Color.Unspecified
    private var background: Color = Color.Unspecified
    private var tints: List<Color> = emptyList()
    private val scrolls = mutableListOf<Double>()
    private val zooms = mutableListOf<Pair<Double, Double>>()
    private var fits = 0
    private var slopPx = 0f

    /** Drums on row 0 over beats 0 to 8 (0 to 16 dp); bass on row 1 over beats 100 to 120 (200 to 240 dp). */
    private val tracks: ImmutableList<TrackUi> = persistentListOf(
        track("t0", colorIndex = 0, FakeOverviews.clip("c0", "drums", startBeats = 0.0, lengthBeats = 8.0)),
        track("t1", colorIndex = 1, FakeOverviews.clip("c1", "bass", startBeats = 100.0, lengthBeats = 20.0)),
    )

    private fun track(id: String, colorIndex: Int, clip: ClipUi) = TrackUi(
        id = TrackId(id), name = id, label = null, colorIndex = colorIndex, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(clip),
    )

    private fun viewport(density: Float) = port?.invoke(density) ?: TimelineViewport(
        pixelsPerBeat = 8.0, scrollBeats = Beats(20.0), widthPx = 320.0 * density, densityScale = density.toDouble(),
        projectLengthBeats = Beats(160.0), beatsPerBar = 4,
    )

    /** When set, the viewport the strip shows instead of the default above. */
    private var port: ((Float) -> TimelineViewport)? = null

    /** The default viewport resting at the start behind a 40 dp lead-in. */
    private fun restingAtTheStart(density: Float): TimelineViewport = TimelineViewport(
        pixelsPerBeat = 8.0, scrollBeats = Beats(-5.0), widthPx = 320.0 * density, densityScale = density.toDouble(),
        projectLengthBeats = Beats(160.0), beatsPerBar = 4, leadInPx = 40.0 * density,
    )

    private fun ComposeUiTest.setStrip(
        playing: Boolean = false,
        positionBeats: Beats = Beats(80.0),
        telemetry: ScriptedTransportTelemetry = ScriptedTransportTelemetry.stopped(0.0),
        height: Dp = OverviewStripDefaults.CompactHeight,
    ) {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                slopPx = LocalViewConfiguration.current.touchSlop
                playhead = TembraTheme.daw.playhead
                background = TembraTheme.daw.surfaceLevel1
                tints = TembraTheme.daw.trackTints
                Box(Modifier.width(STRIP_DP.dp)) {
                    OverviewStrip(
                        viewport = viewport(densityScale), tracks = tracks, playing = playing, positionBeats = positionBeats,
                        telemetry = telemetry, onScrollTo = { scrolls += it.value }, onZoomTo = { start, end -> zooms += start.value to end.value },
                        onFit = { fits++ },
                        height = height,
                        margin = 0.dp,
                        gestureInsets = NO_INSETS,
                    )
                }
            }
        }
    }

    private fun px(dp: Float): Int = (dp * densityScale).roundToInt()

    /** The strip's x in dp for a beat of the 160-beat project. */
    private fun dpAtBeat(beats: Float): Float = beats * STRIP_DP / 160f

    /** The centre of the columns in the playhead colour along the strip's bottom padding (no blocks there), or null. */
    private fun ComposeUiTest.playheadX(): Int? {
        val pixels = onNodeWithTag(TimelineTestTags.OVERVIEW).captureToImage().toPixelMap()
        val y = pixels.height - px(2f)
        val columns = (0 until pixels.width).filter { x -> pixels[x, y].distanceTo(playhead) < 0.15f }
        return if (columns.isEmpty()) null else (columns.sum().toDouble() / columns.size).roundToInt()
    }

    private fun PixelMap.isNear(x: Int, y: Int, color: Color): Boolean = this[x, y].distanceTo(color) < 0.1f

    @Test
    fun theWindowSpansTheVisibleBeatsAndTheStateNamesTheirBars() = runComposeUiTest {
        setStrip()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW)
            .assertContentDescriptionEquals("Overview")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "bars 6 to 15 in view"))
            .assertHeightIsEqualTo(24.dp)
        val bounds = strip.getUnclippedBoundsInRoot()
        val window = onNodeWithTag(TimelineTestTags.OVERVIEW_WINDOW).getUnclippedBoundsInRoot()
        assertTrue(abs((window.left - bounds.left).value - dpAtBeat(20f)) <= 1f, "the window starts at ${window.left - bounds.left}, not beat 20")
        assertTrue(abs((window.right - bounds.left).value - dpAtBeat(60f)) <= 1f, "the window ends at ${window.right - bounds.left}, not beat 60")
    }

    /**
     * The owner's verdict on D1 (spec FR-015; T151): by default the strip keeps at least the 16 dp page margin (more
     * where the system's back-gesture zone is wider) from each side of its host, so a finger reaches either handle
     * without starting a back swipe; the tagged node, which takes the strip's gestures, is the inset strip alone.
     */
    @Test
    fun theDefaultInsetKeepsTheStripClearOfBothSides() = runComposeUiTest {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                Box(Modifier.requiredWidth(STRIP_DP.dp).testTag(HOST)) {
                    OverviewStrip(
                        viewport = viewport(densityScale), tracks = tracks, playing = false, positionBeats = Beats(80.0),
                        telemetry = ScriptedTransportTelemetry.stopped(0.0), onScrollTo = {}, onZoomTo = { _, _ -> },
                    )
                }
            }
        }
        val host = onNodeWithTag(HOST).getUnclippedBoundsInRoot()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW).assertHeightIsEqualTo(24.dp).getUnclippedBoundsInRoot()
        // One pixel of rounding room at the test devices' densities (2.44 and 2.625).
        val margin = 16.dp - (1f / densityScale).dp
        assertTrue(strip.left - host.left >= margin, "the strip starts ${strip.left - host.left} from the host's left edge")
        assertTrue(host.right - strip.right >= margin, "the strip ends ${host.right - strip.right} from the host's right edge")
        // The window still spans beats 20 to 60 of the strip's own (inset) width.
        val width = (strip.right - strip.left).value
        val window = onNodeWithTag(TimelineTestTags.OVERVIEW_WINDOW).getUnclippedBoundsInRoot()
        assertTrue(abs((window.left - strip.left).value - 20f * width / 160f) <= 1f, "the window starts at ${window.left - strip.left} of $width dp")
        assertTrue(abs((window.right - strip.left).value - 60f * width / 160f) <= 1f, "the window ends at ${window.right - strip.left} of $width dp")
    }

    /**
     * T153: the gesture part of the inset counts what the ancestors already padded and consumed. Under a host that
     * consumed 200 dp at the start (the expanded Timeline's header column, wider than any back-gesture zone) the strip
     * starts exactly the 16 dp page margin in, not the margin plus the zone again; at the end, where nothing was
     * consumed, it keeps at least the margin.
     */
    @Test
    fun theGestureInsetCountsWhatAnAncestorConsumed() = runComposeUiTest {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                Box(Modifier.requiredWidth(STRIP_DP.dp).consumeWindowInsets(PaddingValues(start = 200.dp)).testTag(HOST)) {
                    OverviewStrip(
                        viewport = viewport(densityScale), tracks = tracks, playing = false, positionBeats = Beats(80.0),
                        telemetry = ScriptedTransportTelemetry.stopped(0.0), onScrollTo = {}, onZoomTo = { _, _ -> },
                    )
                }
            }
        }
        val host = onNodeWithTag(HOST).getUnclippedBoundsInRoot()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW).getUnclippedBoundsInRoot()
        // One pixel of rounding room at the test devices' densities.
        val onePixel = (1f / densityScale).dp
        val start = strip.left - host.left
        assertTrue(abs((start - 16.dp).value) <= onePixel.value, "the strip starts $start from the host's left edge, not 16 dp")
        assertTrue(host.right - strip.right >= 16.dp - onePixel, "the strip ends ${host.right - strip.right} from the host's right edge")
    }

    @Test
    fun aTallerStripIs28dp() = runComposeUiTest {
        setStrip(height = OverviewStripDefaults.Height)
        onNodeWithTag(TimelineTestTags.OVERVIEW).assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun theClipsAreBlocksInTheirTrackColoursAndTheStoppedLineSitsAtThePosition() = runComposeUiTest {
        setStrip(positionBeats = Beats(80.0))
        val pixels = onNodeWithTag(TimelineTestTags.OVERVIEW).captureToImage().toPixelMap()
        // 24 dp: 3 dp padding, then two 9 dp rows (an 8 dp block and a 1 dp gap each).
        val row0 = px(7f)
        val row1 = px(16f)
        val drums = px(dpAtBeat(4f))
        val bass = px(dpAtBeat(110f))
        assertTrue(pixels.isNear(drums, row0, tints[0]), "no drums block at beat 4 on row 0: ${pixels[drums, row0]}")
        assertTrue(pixels.isNear(bass, row1, tints[1]), "no bass block at beat 110 on row 1: ${pixels[bass, row1]}")
        assertTrue(pixels.isNear(bass, row0, background), "row 0 has no clip at beat 110: ${pixels[bass, row0]}")
        assertTrue(pixels.isNear(drums, row1, background), "row 1 has no clip at beat 4: ${pixels[drums, row1]}")
        val x = playheadX()
        assertTrue(x != null && abs(x - px(dpAtBeat(80f))) <= px(1f), "the stopped line is not at beat 80 (${dpAtBeat(80f)} dp): $x")
    }

    @Test
    fun whilePlayingTheLineReadsTheTelemetryEveryFrame() = runComposeUiTest {
        // Paused before composing: the test clock cancels infinite animations (the frame tick) while it auto-advances.
        mainClock.autoAdvance = false
        // From beat 80 at 40 beats a second: the line starts at beat 80 and moves 40 beats (80 dp) a second.
        val telemetry = ScriptedTransportTelemetry.playing(startSeconds = 2.0, beatsPerSecond = 40.0)
        setStrip(playing = true, positionBeats = Beats.Zero, telemetry = telemetry)
        mainClock.advanceTimeByFrame()
        val first = playheadX()
        val readsBefore = telemetry.reads
        assertTrue(readsBefore > 0, "the strip never read the telemetry")
        mainClock.advanceTimeBy(1_000)
        val later = playheadX()
        assertTrue(telemetry.reads > readsBefore, "the strip stopped reading the telemetry")
        assertTrue(first != null && later != null, "no playhead line while playing: $first, $later")
        assertTrue(later > first + px(dpAtBeat(20f)), "the line did not move: $first → $later")
    }

    @Test
    fun draggingInsideTheWindowScrollsWithTheFingerAndStopsAtTheEnd() = runComposeUiTest {
        setStrip()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(40f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(dpAtBeat(2f).dp.toPx(), 0f)) }
            up()
        }
        assertTrue(zooms.isEmpty(), "a drag inside the window does not zoom: $zooms")
        // 40 dp to the right is 20 beats, less the touch slop the drag starts after: the window starts that far past beat 20.
        assertEquals(20.0 + (40f - slopDp) * 160.0 / STRIP_DP, scrolls.last(), 0.05)
        scrolls.clear()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(40f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(dpAtBeat(12f).dp.toPx(), 0f)) }
            up()
        }
        // Held inside the project: 160 beats less the 40 in view.
        assertEquals(120.0, scrolls.last(), 1e-6)
    }

    /** Behind the lead-in the left edge is a negative beat: the strip still names bar 1, and its window scrolls no earlier than the lead-in. */
    @Test
    fun aViewAtTheStartNamesBarOneAndItsWindowStopsAtTheLeadIn() = runComposeUiTest {
        port = ::restingAtTheStart
        setStrip()
        onNodeWithTag(TimelineTestTags.OVERVIEW)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "bars 1 to 9 in view"))
            .performTouchInput {
                down(Offset(dpAtBeat(20f).dp.toPx(), centerY))
                repeat(10) { moveBy(Offset(-dpAtBeat(4f).dp.toPx(), 0f)) }
                up()
            }
        assertEquals(-5.0, scrolls.last(), 1e-6)
    }

    private val slopDp: Float get() = slopPx / densityScale

    /** A press at [startDp] along the strip's middle, the finger moved by ([dxDp], [dyDp]) (positive y is down) in [steps] moves, then lifted. */
    private fun ComposeUiTest.dragBy(startDp: Float, dxDp: Float, dyDp: Float, steps: Int = 1) {
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            val from = Offset(startDp.dp.toPx(), centerY)
            down(from)
            for (step in 1..steps) {
                moveTo(from + Offset(dxDp.dp.toPx() * step / steps, dyDp.dp.toPx() * step / steps))
            }
            up()
        }
    }

    private fun assertWindow(expectedStart: Double, expectedEnd: Double, actual: Pair<Double, Double>, tolerance: Double = 0.05) {
        assertEquals(expectedStart, actual.first, tolerance, "window start of $actual")
        assertEquals(expectedEnd, actual.second, tolerance, "window end of $actual")
    }

    /** Beats 20 to 60 are in view: the centre is beat 40 and the span 40 beats; 60 dp of travel past the slop halves or doubles the span. */
    @Test
    fun draggingUpFromTheLeftHandleZoomsInAroundTheWindowsCentre() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(20f), dxDp = 0f, dyDp = -(ZOOM_DP + slopDp))
        assertTrue(scrolls.isEmpty(), "a vertical drag does not scroll: $scrolls")
        assertWindow(30.0, 50.0, zooms.last())
        zooms.clear()
        dragBy(startDp = dpAtBeat(20f), dxDp = 0f, dyDp = -(2 * ZOOM_DP + slopDp))
        assertWindow(35.0, 45.0, zooms.last())
    }

    @Test
    fun draggingUpFromOutsideTheWindowZoomsInAroundTheSameCentre() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(140f), dxDp = 0f, dyDp = -(ZOOM_DP + slopDp))
        assertTrue(scrolls.isEmpty(), "a vertical drag does not scroll: $scrolls")
        assertWindow(30.0, 50.0, zooms.last())
    }

    @Test
    fun aLongDragUpStopsAtTheClosestLevelOfOneBeat() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(140f), dxDp = 0f, dyDp = -600f)
        assertWindow(39.5, 40.5, zooms.last(), tolerance = 1e-6)
    }

    @Test
    fun draggingDownZoomsOutAroundTheWindowsCentreAndStopsAtTheFit() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(60f), dxDp = 0f, dyDp = ZOOM_DP + slopDp)
        assertTrue(scrolls.isEmpty(), "a vertical drag does not scroll: $scrolls")
        assertWindow(0.0, 80.0, zooms.last())
        zooms.clear()
        dragBy(startDp = dpAtBeat(140f), dxDp = 0f, dyDp = 600f)
        // The fit shows the whole 160 beats, from the start.
        assertWindow(0.0, 160.0, zooms.last(), tolerance = 1e-6)
    }

    /** The window's centre follows the finger's distance from the press (less the slop), not the finger's position: the first frame does not jump. */
    @Test
    fun aSidewaysDragFromOutsideTheWindowMovesItByTheFingersDistanceWithoutAJump() = runComposeUiTest {
        setStrip()
        // Pressed at beat 120, well to the right of the window (beats 20 to 60).
        dragBy(startDp = dpAtBeat(120f), dxDp = slopDp * 1.5f, dyDp = 0f)
        // Half the slop's beats: the first frame is the distance past the slop, not the finger's beat (120).
        assertEquals(20.0 + 0.5f * slopDp * 160.0 / STRIP_DP, scrolls.first(), 0.05, "the window jumped when the drag began")
        scrolls.clear()
        dragBy(startDp = dpAtBeat(120f), dxDp = 40f, dyDp = 0f, steps = 4)
        assertTrue(zooms.isEmpty(), "a sideways drag does not zoom: $zooms")
        // 40 dp is 20 beats, less the slop: the window starts that far past beat 20.
        assertEquals(20.0 + (40f - slopDp) * 160.0 / STRIP_DP, scrolls.last(), 0.05)
    }

    @Test
    fun aMostlySidewaysDiagonalDragOnlyScrolls() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(40f), dxDp = 80f, dyDp = -30f, steps = 4)
        assertTrue(zooms.isEmpty(), "a mostly sideways drag never zooms: $zooms")
        assertTrue(scrolls.isNotEmpty())
    }

    @Test
    fun aMostlyVerticalDiagonalDragOnlyZooms() = runComposeUiTest {
        setStrip()
        dragBy(startDp = dpAtBeat(40f), dxDp = 30f, dyDp = -80f, steps = 4)
        assertTrue(scrolls.isEmpty(), "a mostly vertical drag never scrolls: $scrolls")
        assertTrue(zooms.isNotEmpty())
    }

    @Test
    fun aDragKeepsTheDirectionItStartedWith() = runComposeUiTest {
        setStrip()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            val from = Offset(dpAtBeat(40f).dp.toPx(), centerY)
            down(from)
            moveTo(from + Offset(slopPx * 1.5f, 0f))
            moveTo(from + Offset(slopPx * 1.5f, -100.dp.toPx()))
            up()
        }
        assertTrue(zooms.isEmpty(), "a drag that began sideways never zooms: $zooms")
        scrolls.clear()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            val from = Offset(dpAtBeat(40f).dp.toPx(), centerY)
            down(from)
            moveTo(from + Offset(0f, -slopPx * 1.5f))
            moveTo(from + Offset(100.dp.toPx(), -slopPx * 1.5f))
            up()
        }
        assertTrue(scrolls.isEmpty(), "a drag that began vertically never scrolls: $scrolls")
    }

    /** Presses inside the window, so only the double tap's own animation calls back. */
    /** Two taps at [atDp], the second pressed [gapMillis] after the first's release (by default a finger's pace). */
    private fun ComposeUiTest.doubleTap(atDp: Float, gapMillis: Long? = null) {
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            val at = Offset(atDp.dp.toPx(), centerY)
            down(at)
            up()
            advanceEventTime(gapMillis ?: (viewConfiguration.doubleTapMinTimeMillis + 50))
            down(at)
            up()
        }
    }

    /** XCUITest's doubleTap() presses again at the first release's very time; the strip takes it as a double tap. */
    @Test
    fun aSecondPressAtTheFirstReleasesTimeStillFits() = runComposeUiTest {
        setStrip()
        mainClock.autoAdvance = false
        doubleTap(dpAtBeat(40f), gapMillis = 0)
        mainClock.advanceTimeBy(800)
        assertEquals(1, fits, "a zero-gap double tap did not fit")
    }

    @Test
    fun aDoubleTapAnimatesTheSpanTowardsTheFitThenCallsOnFitOnce() = runComposeUiTest {
        setStrip()
        mainClock.autoAdvance = false
        doubleTap(dpAtBeat(40f))
        assertEquals(0, fits, "nothing has run yet")
        // About 150 ms in: partway, frames already seen, no fit yet.
        repeat(9) { mainClock.advanceTimeByFrame() }
        assertTrue(zooms.size >= 2, "the animation made no frames: $zooms")
        assertEquals(0, fits, "onFit came before the animation ended")
        assertTrue(zooms.last().second - zooms.last().first < 160.0, "the span reached the fit early: ${zooms.last()}")
        mainClock.advanceTimeBy(400)
        assertEquals(1, fits, "onFit exactly once at the end")
        val spans = zooms.map { it.second - it.first }
        assertTrue(spans.zipWithNext().all { (a, b) -> b >= a - 1e-9 }, "the span did not grow monotonically: $spans")
        assertTrue(spans.first() >= 40.0 - 1e-9 && spans.first() < spans.last(), "the span did not start from the window's: $spans")
        assertWindow(0.0, 160.0, zooms.last(), tolerance = 1e-6)
        assertTrue(scrolls.isEmpty(), "the animation zooms only: $scrolls")
        val frames = zooms.size
        mainClock.advanceTimeBy(500)
        assertEquals(frames, zooms.size, "frames after the end")
        assertEquals(1, fits)
    }

    @Test
    fun aPressDuringTheAnimationStopsItAndOnFitIsNeverCalled() = runComposeUiTest {
        setStrip()
        mainClock.autoAdvance = false
        doubleTap(dpAtBeat(40f))
        repeat(6) { mainClock.advanceTimeByFrame() }
        assertTrue(zooms.isNotEmpty(), "the animation made no frames")
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(40f).dp.toPx(), centerY))
            up()
        }
        val frames = zooms.size
        mainClock.advanceTimeBy(1_000)
        assertEquals(frames, zooms.size, "the animation kept running after the press")
        assertEquals(0, fits, "onFit after a cancelled animation")
    }

    @Test
    fun aTapOutsideTheWindowCentresItOnTheTap() = runComposeUiTest {
        setStrip()
        val strip = onNodeWithTag(TimelineTestTags.OVERVIEW)
        // Beat 120: the 40 beats in view start 20 beats before it.
        strip.performTouchInput { click(Offset(dpAtBeat(120f).dp.toPx(), centerY)) }
        assertEquals(listOf(100.0), scrolls.map { (it * 10).roundToInt() / 10.0 })
        strip.performTouchInput { click(Offset(dpAtBeat(40f).dp.toPx(), centerY)) }
        assertEquals(1, scrolls.size, "a tap inside the window changes nothing")
        // Near the end the window is held inside the project.
        strip.performTouchInput { click(Offset(dpAtBeat(157f).dp.toPx(), centerY)) }
        assertEquals(120.0, scrolls.last(), 1e-6)
        assertTrue(zooms.isEmpty())
    }

    private companion object {
        /** Fits the narrowest device window (the 360 dp OPPO) with room to spare. */
        const val STRIP_DP = 320

        /** The strip's vertical travel that doubles or halves the span. */
        const val ZOOM_DP = 60f
        const val HOST = "test.overviewHost"

        /** No gesture zone: with a zero margin the strip is as wide as its host. */
        val NO_INSETS = WindowInsets(0, 0, 0, 0)
    }
}
