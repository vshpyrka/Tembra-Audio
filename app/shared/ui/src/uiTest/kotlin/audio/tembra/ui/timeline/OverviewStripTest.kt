package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
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
 * playhead read from the telemetry in draw, and a window over the visible span that scrolls when dragged, zooms when
 * an edge is dragged and recentres on a tap outside it. Every test uses 40 bars of 4/4 (160 beats) on a 320 dp strip
 * (2 dp a beat; it must fit the narrowest device window, the 360 dp OPPO, since `width` is coerced into the window) and
 * lanes 320 dp wide at 8 dp a beat: 40 beats in view from beat 20, so the window spans 40 to 120 dp. Positions are
 * written as beats through [dpAtBeat].
 */
@OptIn(ExperimentalTestApi::class)
class OverviewStripTest {

    private var densityScale = 1f
    private var playhead: Color = Color.Unspecified
    private var background: Color = Color.Unspecified
    private var tints: List<Color> = emptyList()
    private val scrolls = mutableListOf<Double>()
    private val zooms = mutableListOf<Pair<Double, Double>>()

    /** Drums on row 0 over beats 0 to 8 (0 to 16 dp); bass on row 1 over beats 100 to 120 (200 to 240 dp). */
    private val tracks: ImmutableList<TrackUi> = persistentListOf(
        track("t0", colorIndex = 0, FakeOverviews.clip("c0", "drums", startBeats = 0.0, lengthBeats = 8.0)),
        track("t1", colorIndex = 1, FakeOverviews.clip("c1", "bass", startBeats = 100.0, lengthBeats = 20.0)),
    )

    private fun track(id: String, colorIndex: Int, clip: ClipUi) = TrackUi(
        id = TrackId(id), name = id, label = null, colorIndex = colorIndex, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(clip),
    )

    private fun viewport(density: Float) = TimelineViewport(
        pixelsPerBeat = 8.0, scrollBeats = Beats(20.0), widthPx = 320.0 * density, densityScale = density.toDouble(),
        projectLengthBeats = Beats(160.0), beatsPerBar = 4,
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
                playhead = TembraTheme.daw.playhead
                background = TembraTheme.daw.surfaceLevel1
                tints = TembraTheme.daw.trackTints
                Box(Modifier.width(STRIP_DP.dp)) {
                    OverviewStrip(
                        viewport = viewport(densityScale), tracks = tracks, playing = playing, positionBeats = positionBeats,
                        telemetry = telemetry, onScrollTo = { scrolls += it.value }, onZoomTo = { start, end -> zooms += start.value to end.value },
                        height = height,
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
        // 20 beats to the right: the window now starts at beat 40.
        assertEquals(40.0, scrolls.last(), 0.5)
        scrolls.clear()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(40f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(dpAtBeat(12f).dp.toPx(), 0f)) }
            up()
        }
        // Held inside the project: 160 beats less the 40 in view.
        assertEquals(120.0, scrolls.last(), 1e-6)
    }

    @Test
    fun draggingTheRightHandleZoomsWithTheLeftEdgeFixed() = runComposeUiTest {
        setStrip()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(60f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(dpAtBeat(2f).dp.toPx(), 0f)) }
            up()
        }
        assertTrue(scrolls.isEmpty(), "an edge drag does not scroll: $scrolls")
        val (start, end) = zooms.last()
        assertEquals(20.0, start, 1e-9)
        assertEquals(80.0, end, 0.5)
        zooms.clear()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(60f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(-dpAtBeat(8f).dp.toPx(), 0f)) }
            up()
        }
        // The closest level shows one beat across the lanes.
        assertEquals(20.0, zooms.last().first, 1e-9)
        assertEquals(21.0, zooms.last().second, 1e-6)
    }

    @Test
    fun draggingTheLeftHandleZoomsWithTheRightEdgeFixed() = runComposeUiTest {
        setStrip()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(20f).dp.toPx(), centerY))
            repeat(5) { moveBy(Offset(-dpAtBeat(2f).dp.toPx(), 0f)) }
            up()
        }
        val (start, end) = zooms.last()
        assertEquals(10.0, start, 0.5)
        assertEquals(60.0, end, 1e-9)
        zooms.clear()
        onNodeWithTag(TimelineTestTags.OVERVIEW).performTouchInput {
            down(Offset(dpAtBeat(20f).dp.toPx(), centerY))
            repeat(10) { moveBy(Offset(dpAtBeat(8f).dp.toPx(), 0f)) }
            up()
        }
        // Held at the closest level: one beat before the fixed right edge.
        assertEquals(59.0, zooms.last().first, 1e-6)
        assertEquals(60.0, zooms.last().second, 1e-9)
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
    }
}
