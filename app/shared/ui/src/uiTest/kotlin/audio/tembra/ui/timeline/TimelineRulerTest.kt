package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.designsystem.approximately
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.containsColor
import audio.tembra.ui.designsystem.isCloserTo
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.RULER_DESCRIPTION
import audio.tembra.ui.timeline.components.TimelineRuler
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The ruler driven with a fixed viewport (contracts/timeline-screen.md; T079): labels drawn, taps seek, drags scrub, the
 * chip text and its legibility (FR-009; T139), and its left slot before the canvas (D1, T141); nothing overlays the
 * canvas since the zoom buttons went (T144).
 */
@OptIn(ExperimentalTestApi::class)
class TimelineRulerTest {

    private val tempoMap = TempoMap(Tempo(124), TimeSignature.FourFour)
    private var seeks = mutableListOf<Beats>()
    private var scrubs = mutableListOf<String>()
    private var densityScale = 1f
    private var chip: Color = Color.Unspecified
    private var playhead: Color = Color.Unspecified
    private var viewport: TimelineViewport? = null

    private fun ComposeUiTest.setRuler(
        positionBeats: Beats = Beats(16.0),
        playing: Boolean = false,
        telemetry: ScriptedTransportTelemetry = ScriptedTransportTelemetry.stopped(0.0),
        leftSlot: (@Composable () -> Unit)? = null,
    ) {
        setContent {
            TembraTheme {
                densityScale = LocalDensity.current.density
                chip = TembraTheme.daw.playheadChip
                playhead = TembraTheme.daw.playhead
                // 32 bars across 600 dp: the widest zoom at this width.
                val widthPx = 600.0 * densityScale
                val port = TimelineViewport.fitted(widthPx = widthPx, densityScale = densityScale.toDouble(), projectLengthBeats = Beats(128.0), beatsPerBar = 4)
                viewport = port
                TimelineRuler(
                    viewport = port,
                    tempoMap = tempoMap,
                    playing = playing,
                    positionBeats = positionBeats,
                    telemetry = telemetry,
                    onSeek = { seeks += it },
                    onScrubStart = { scrubs += "start" },
                    onScrub = { scrubs += "scrub" },
                    onScrubEnd = { scrubs += "end" },
                    modifier = Modifier.width(600.dp).testTag(ROW),
                    leftSlot = leftSlot,
                )
            }
        }
        mainClock.autoAdvance = false
    }

    /** Bars and time only (spec FR-013, D1): with no slots the canvas is the whole row and carries no pill. */
    @Test
    fun isA44dpRulerSpanningItsRowWithTheChipTextInItsSemantics() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        val row = onNodeWithTag(ROW).getUnclippedBoundsInRoot()
        onNodeWithTag(TimelineTestTags.RULER)
            .assertHeightIsEqualTo(44.dp)
            .assertWidthIsEqualTo(row.right - row.left)
            .assertLeftPositionInRootIsEqualTo(row.left)
            .assertContentDescriptionEquals(RULER_DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5.1"))
        assertEquals(0, onAllNodes(hasClickAction()).fetchSemanticsNodes().size, "the ruler carries no tempo or signature pill")
    }

    /**
     * The expanded layout's empty 200 dp left slot puts the canvas's start 200 dp in, nothing between them, and the
     * canvas runs to the row's right end, so its scale stays the lanes' (FR-017).
     */
    @Test
    fun theLeftSlotShiftsTheCanvasToTheRowsEnd() = runComposeUiTest {
        setRuler(leftSlot = { Spacer(Modifier.width(200.dp)) })
        val row = onNodeWithTag(ROW).getUnclippedBoundsInRoot()
        val canvas = onNodeWithTag(TimelineTestTags.RULER).assertWidthIsEqualTo(row.right - row.left - 200.dp).getUnclippedBoundsInRoot()
        assertTrue(abs((canvas.left - row.left - 200.dp).value) < 0.5f, "the canvas starts at ${canvas.left}, not right after the 200 dp slot at ${row.left + 200.dp}")
        assertTrue(abs((canvas.right - row.right).value) < 0.5f, "the canvas ends at ${canvas.right}, not at the row's right end ${row.right}")
        assertEquals(0, onAllNodes(hasClickAction()).fetchSemanticsNodes().size, "nothing clickable overlays the canvas")
    }

    @Test
    fun drawsTheChipAtThePlayheadAndLabelsAcrossTheRow() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        val port = assertNotNull(viewport)
        val image = onNodeWithTag(TimelineTestTags.RULER).captureToImage()
        val x = port.xOf(Beats(16.0)).toInt().coerceIn(0, image.width - 1)
        assertTrue(image.containsColor(chip), "no playhead chip was drawn")
        assertTrue(image.containsColor(playhead), "no playhead marker was drawn")
        // The chip's colour sits around the playhead x in the bar row.
        val probe = image.averageColorAt(x, image.height / 4, radius = 2)
        assertTrue(probe.isCloserTo(chip, Color.Black), "the chip is not at the playhead x: $probe")
    }

    @Test
    fun theChipTextContrastsWithTheChip() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        val port = assertNotNull(viewport)
        val pixels = onNodeWithTag(TimelineTestTags.RULER).captureToImage().toPixelMap()
        // The chip's text is centred on the playhead x in the bar row (the top half): a 16 × 12 dp block around it holds
        // the glyphs and the chip's fill around them, inside the outline.
        val x = port.xOf(Beats(16.0)).roundToInt()
        val y = pixels.height / 4
        val halfWidth = (8 * densityScale).roundToInt()
        val halfHeight = (6 * densityScale).roundToInt()
        val block = buildList {
            for (py in y - halfHeight..y + halfHeight) for (px in x - halfWidth..x + halfWidth) add(pixels[px.coerceIn(0, pixels.width - 1), py.coerceIn(0, pixels.height - 1)])
        }
        val fill = Color(block.groupingBy { it.toArgb() }.eachCount().maxBy { it.value }.key)
        assertTrue(fill.approximately(chip), "the block at the playhead is $fill, not the chip $chip")
        val text = block.maxBy { contrast(it, fill) }
        val ratio = contrast(text, fill)
        assertTrue(ratio >= 4.5f, "the chip text $text contrasts with the chip $fill at $ratio:1, below 4.5:1")
    }

    @Test
    fun theChipHasAOneDpOutlineInThePlayheadColour() = runComposeUiTest {
        setRuler(positionBeats = Beats(16.0))
        val port = assertNotNull(viewport)
        val image = onNodeWithTag(TimelineTestTags.RULER).captureToImage()
        val x = port.xOf(Beats(16.0)).roundToInt()
        // The chip's top edge is 3 dp below the ruler's: the pixel row holding 3.5 dp lies inside the 1 dp outline (floored:
        // at 1× the outline is row 3 alone and 3.5 rounds to the fill's row 4), 2 dp further the fill.
        val edge = image.averageColorAt(x, (3.5f * densityScale).toInt(), radius = 0)
        assertTrue(edge.isCloserTo(playhead, chip), "the chip's top edge is $edge, not the playhead colour $playhead")
        val inside = image.averageColorAt(x, (5.5f * densityScale).roundToInt(), radius = 0)
        assertTrue(inside.isCloserTo(chip, playhead), "inside the outline is $inside, not the chip $chip")
    }

    @Test
    fun aTapSeeksToTheBeatUnderTheFinger() = runComposeUiTest {
        setRuler()
        mainClock.autoAdvance = true
        val port = assertNotNull(viewport)
        onNodeWithTag(TimelineTestTags.RULER).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        assertEquals(1, seeks.size)
        val canvasWidthPx = onNodeWithTag(TimelineTestTags.RULER).fetchSemanticsNode().size.width.toDouble()
        val expected = port.beatsAt(canvasWidthPx / 2).value
        assertTrue(abs(seeks.single().value - expected) < 0.5, "seeked to ${seeks.single().value}, expected about $expected")
    }

    @Test
    fun aDragScrubsFromStartToEnd() = runComposeUiTest {
        setRuler()
        mainClock.autoAdvance = true
        onNodeWithTag(TimelineTestTags.RULER).performTouchInput { swipe(Offset(width * 0.2f, height / 2f), Offset(width * 0.6f, height / 2f), durationMillis = 300) }
        assertEquals("start", scrubs.first())
        assertEquals("end", scrubs.last())
        assertTrue(scrubs.count { it == "scrub" } >= 2, "the drag reported ${scrubs.count { it == "scrub" }} positions")
        assertTrue(seeks.isEmpty(), "a drag must not seek")
    }

    @Test
    fun whilePlayingTheChipFollowsTheTelemetry() = runComposeUiTest {
        val telemetry = ScriptedTransportTelemetry.playing(startSeconds = 0.0)
        setRuler(playing = true, telemetry = telemetry)
        mainClock.advanceTimeBy(200)
        assertTrue(telemetry.reads > 0, "the ruler did not read the telemetry while playing")
    }

    private companion object {
        const val ROW = "ruler.row"
    }

    /** The WCAG contrast ratio of two opaque colours. */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
