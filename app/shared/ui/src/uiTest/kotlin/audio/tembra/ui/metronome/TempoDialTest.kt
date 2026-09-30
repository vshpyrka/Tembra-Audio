package audio.tembra.ui.metronome

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.metronome.components.TempoDial
import audio.tembra.ui.metronome.components.TempoDialGeometry
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tempo dial (contracts/design-system-additions.md, research R8): readout, semantics, steppers, ring drag, wheel and fill. */
@OptIn(ExperimentalTestApi::class)
class TempoDialTest {

    private val tempos = mutableListOf<Tempo>()
    private val steps = mutableListOf<Int>()
    private var track: Color = Color.Unspecified
    private var primary: Color = Color.Unspecified
    private var secondary: Color = Color.Unspecified

    private fun ComposeUiTest.setDial(
        tempo: Tempo = Tempo(120),
        canStepUp: Boolean = true,
        canStepDown: Boolean = true,
        freezeClock: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
    ) {
        if (freezeClock) mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                track = TembraTheme.colors.surfaceContainerHigh
                primary = TembraTheme.colors.primary
                secondary = TembraTheme.colors.secondary
                Box(Modifier.width(270.dp)) {
                    TempoDial(
                        tempo = tempo,
                        marking = TempoMarking.of(tempo),
                        canStepDown = canStepDown,
                        canStepUp = canStepUp,
                        onTempo = { tempos += it },
                        onStep = { steps += it },
                        interactionSource = interactionSource,
                        testTag = "dial",
                    )
                }
            }
        }
        if (freezeClock) repeat(3) { mainClock.advanceTimeByFrame() } else waitForIdle()
    }

    /** A point on the ring [degrees] clockwise from 12 o'clock, in the dial's own coordinates. */
    private fun ringPoint(width: Float, height: Float, degrees: Float): Offset {
        val radians = degrees * PI / 180.0
        val radius = TempoDialGeometry.RING_RADIUS * minOf(width, height)
        return Offset(width / 2 + radius * sin(radians).toFloat(), height / 2 - radius * cos(radians).toFloat())
    }

    @Test
    fun showsTheReadout() = runComposeUiTest {
        setDial()
        onNodeWithText("120").assertExists()
        onNodeWithText("BPM").assertExists()
        onNodeWithText("Moderato (108-120)").assertExists()
    }

    @Test
    fun isOneSliderNode() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(120f, 40f..240f, steps = 199)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "120 beats per minute, Moderato"))
    }

    @Test
    fun steppersStepByOne() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial.up").performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("dial.down").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun theUpStepperIsDisabledAtTheTop() = runComposeUiTest {
        setDial(tempo = Tempo(240), canStepUp = false)
        onNodeWithTag("dial.up").assertIsNotEnabled()
        onNodeWithTag("dial.down").assertIsEnabled()
    }

    @Test
    fun draggingOnTheRingMapsTheAngle() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial").performTouchInput {
            val top = ringPoint(width.toFloat(), height.toFloat(), 0f)
            val right = ringPoint(width.toFloat(), height.toFloat(), 90f)
            down(top)
            moveTo(Offset(top.x + (right.x - top.x) / 2, top.y + (right.y - top.y) / 4))
            moveTo(right)
            up()
        }
        mainClock.advanceTimeByFrame()
        val last = tempos.last()
        assertTrue(abs(last.bpm - 105) <= 1, "a quarter turn gave $last")
    }

    @Test
    fun aDragThatStartsInTheCentreDoesNothing() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial").performTouchInput {
            down(center)
            moveTo(ringPoint(width.toFloat(), height.toFloat(), 200f))
            up()
        }
        mainClock.advanceTimeByFrame()
        assertEquals(emptyList(), tempos)
    }

    /** The steppers sit on the ring band: a press that moves past the touch slop on one must not turn the dial. */
    @Test
    fun aSmallDragOnAStepperNeverTurnsTheDial() = runComposeUiTest {
        setDial()
        for (stepper in listOf("dial.down", "dial.up")) {
            onNodeWithTag(stepper).performTouchInput {
                val slop = viewConfiguration.touchSlop * 1.5f
                down(center)
                moveBy(Offset(slop, slop / 2))
                moveBy(Offset(slop / 2, slop / 2))
                up()
            }
            mainClock.advanceTimeByFrame()
        }
        assertEquals(emptyList(), tempos)
    }

    @Test
    fun theWheelStepsByOne() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial").performMouseInput { scroll(-1f) }
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(Tempo(121)), tempos)
    }

    @Test
    fun holdingAStepperRepeatsAfterTheDelay() = runComposeUiTest {
        setDial()
        onNodeWithTag("dial.up").performTouchInput { down(center) }
        mainClock.advanceTimeBy(400)
        mainClock.advanceTimeBy(160)
        onNodeWithTag("dial.up").performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(1, 1, 1), steps)
    }

    @Test
    fun theFillReachesTheTempo() = runComposeUiTest {
        setDial()
        val image = onNodeWithTag("dial").captureToImage()
        val point = ringPoint(image.width.toFloat(), image.height.toFloat(), 110f)
        val fill = image.averageColorAt(point.x.roundToInt(), point.y.roundToInt())
        assertTrue(fill.distanceTo(track) > 0.2f, "the ring at 110° is $fill, the track colour: not filled")
        val beyond = ringPoint(image.width.toFloat(), image.height.toFloat(), 200f)
        val empty = image.averageColorAt(beyond.x.roundToInt(), beyond.y.roundToInt())
        assertTrue(empty.distanceTo(track) < fill.distanceTo(track), "the ring at 200° ($empty) is filled like the tempo's arc")
    }

    /**
     * The design's SVG gradient runs from its top-left to its bottom-right and the SVG is turned −90°, so on screen it
     * runs from bottom-left (`primary`) to top-right (`secondary`): a full ring starts warm at 12 o'clock and ends cool
     * near 9 o'clock.
     */
    @Test
    fun theRingGradientRunsFromBottomLeftToTopRight() = runComposeUiTest {
        setDial(tempo = Tempo(240), canStepUp = false)
        val image = onNodeWithTag("dial").captureToImage()
        fun at(degrees: Float): Color {
            val point = ringPoint(image.width.toFloat(), image.height.toFloat(), degrees)
            return image.averageColorAt(point.x.roundToInt(), point.y.roundToInt())
        }
        val start = at(4f)
        val end = at(250f) // clear of the "−" stepper at 9 o'clock
        assertTrue(start.red - start.blue > end.red - end.blue + 0.05f, "the fill is not warmer at its start ($start) than at its end ($end)")
        assertTrue(start.distanceTo(secondary) < end.distanceTo(secondary), "the start ($start) is not nearer secondary than the end ($end)")
        assertTrue(end.distanceTo(primary) < start.distanceTo(primary), "the end ($end) is not nearer primary than the start ($start)")
    }

    /** Keyboard focus draws a `primary` ring around the dial (injected interaction source). */
    @Test
    fun focusDrawsARing() = runComposeUiTest {
        val source = MutableInteractionSource()
        setDial(interactionSource = source)
        fun ringPixels(): Int {
            val image = onNodeWithTag("dial").captureToImage()
            val side = minOf(image.width, image.height).toFloat()
            // Sample the circle at the focus ring's radius, outside the tempo ring and its ticks.
            return (0 until 72).count { i ->
                val radians = i * 5.0 * PI / 180.0
                val radius = TempoDialGeometry.FOCUS_RING_RADIUS * side
                val x = (image.width / 2f + radius * sin(radians).toFloat()).roundToInt()
                val y = (image.height / 2f - radius * cos(radians).toFloat()).roundToInt()
                image.averageColorAt(x, y, radius = 0).distanceTo(primary) < 0.25f
            }
        }
        val before = ringPixels()
        runOnIdle { source.tryEmit(FocusInteraction.Focus()) }
        repeat(2) { mainClock.advanceTimeByFrame() }
        val after = ringPixels()
        assertTrue(before < 10 && after > 55, "no focus ring: $before primary samples before focus, $after after")
    }

    @Test
    fun steppersShowTheirTooltipOnHover() = runComposeUiTest {
        setDial(freezeClock = false)
        onNodeWithTag("dial.up").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("Increase tempo")).assertExists()
        onNodeWithTag("dial.up").performMouseInput { exit() }
        onNodeWithTag("dial.down").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("Decrease tempo")).assertExists()
    }
}
