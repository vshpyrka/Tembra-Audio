package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.LocalDawColors
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.CLIP_INDICATOR
import audio.tembra.ui.timeline.components.InputMeter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The input meter (feature 010, spec FR-005, FR-006; contracts/timeline-recording-ui.md; T058): the scripted levels, the
 * clip latch and its reset, the hold mark under a frozen clock, both heights, the semantics; semantics before pixels,
 * the pixels read against opaque stand-ins for the meter's tokens.
 */
@OptIn(ExperimentalTestApi::class)
class InputMeterTest {

    private val body = Color(0xFF00FF00)
    private val warning = Color(0xFFFFFF00)
    private val clip = Color(0xFFFF00FF)
    private val meterColors = DawColors.Default.copy(meterBody = body, meterWarning = warning, meterClip = clip)
    private val resets = mutableListOf<Int>()

    private fun ComposeUiTest.setMeter(telemetry: ScriptedInputTelemetry, height: Dp = 4.dp, reduceMotion: Boolean = false, armed: Boolean = true) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                CompositionLocalProvider(
                    LocalDawColors provides meterColors,
                    LocalTembraMotion provides TembraTheme.motion.copy(reduceMotion = reduceMotion),
                ) {
                    Box(Modifier.requiredWidth(WIDTH)) {
                        InputMeter(
                            telemetry = telemetry,
                            armed = armed,
                            onResetClip = { resets += 1 },
                            modifier = Modifier.fillMaxWidth(),
                            height = height,
                            testTag = METER,
                            clipTestTag = CLIP,
                        )
                    }
                }
            }
        }
    }

    /** The x of every pixel of [color] on the meter's middle row. */
    private fun ComposeUiTest.columnsOf(color: Color): List<Int> {
        val pixels = onNodeWithTag(METER).captureToImage().toPixelMap()
        val y = pixels.height / 2
        return (0 until pixels.width).filter { x -> close(pixels[x, y], color) }
    }

    /** The rounded ends of the fill and the track cost up to the bar's height in pixels on the middle row at any density. */
    private fun ComposeUiTest.tolerancePx(): Int = with(density) { 8.dp.roundToPx() } + 2

    private fun close(a: Color, b: Color): Boolean = abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f

    private fun ComposeUiTest.trackWidthPx(): Int {
        val pixels = onNodeWithTag(METER).captureToImage().toPixelMap()
        // The clip square is as wide as the bar is tall, 2 dp before it.
        return pixels.width - pixels.height - with(density) { 2.dp.roundToPx() }
    }

    @Test
    fun silenceDrawsNothingAndAnnouncesTheFloor() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.silent())
        mainClock.advanceTimeByFrame()
        onNodeWithTag(METER).assertContentDescriptionEquals("Input level").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−60 dB"))
        assertEquals(emptyList(), columnsOf(body))
        onNodeWithTag(CLIP).assertContentDescriptionEquals(CLIP_INDICATOR).assertHasNoClickAction()
    }

    @Test
    fun minusTwentyDecibelsFillsTwoThirdsOfTheTrack() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.level(0.1f))
        mainClock.advanceTimeBy(200)
        val filled = columnsOf(body).size
        val expected = (trackWidthPx() * 2 / 3f).toInt()
        assertTrue(abs(filled - expected) <= tolerancePx(), "filled $filled px, expected about $expected")
        assertEquals(emptyList(), columnsOf(warning))
        onNodeWithTag(METER).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−20 dB"))
    }

    @Test
    fun aboveMinusSixTheWarningColourTakesOverAndFullScaleLightsTheClipSquare() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.level(1f, clipped = true))
        mainClock.advanceTimeBy(200)
        assertTrue(columnsOf(warning).isNotEmpty(), "the warning colour above −6 dB")
        assertTrue(columnsOf(clip).isNotEmpty(), "the clip square lit")
        onNodeWithTag(CLIP).assertContentDescriptionEquals("Clipped, tap to reset").assertHasClickAction().performClick()
        assertEquals(listOf(1), resets)
        onNodeWithTag(METER).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 dB"))
    }

    @Test
    fun theHoldMarkStaysOneAndAHalfSecondsThenFalls() = runComposeUiTest {
        val telemetry = ScriptedInputTelemetry { nanos ->
            CaptureSnapshot().apply {
                armed = true
                sampleRate = 48000
                inputChannels = 1
                val peak = if (nanos < 200_000_000L) 0.5f else 0.01f
                this.peak[0] = peak
                this.peak[1] = peak
            }
        }
        var mark = Color.Unspecified
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                mark = TembraTheme.colors.onSurface
                CompositionLocalProvider(LocalDawColors provides meterColors) {
                    Box(Modifier.requiredWidth(WIDTH)) {
                        InputMeter(telemetry = telemetry, armed = true, onResetClip = {}, modifier = Modifier.fillMaxWidth(), testTag = METER, clipTestTag = CLIP)
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        val held = columnsOf(mark)
        val track = trackWidthPx()
        assertTrue(held.isNotEmpty() && held.max() >= track * 0.85f, "the mark near −6 dB after 1 s: $held of $track")
        mainClock.advanceTimeBy(2_000)
        val fallen = columnsOf(mark)
        assertTrue(fallen.isEmpty() || fallen.max() < track * 0.6f, "the mark fallen after 3 s: $fallen of $track")
    }

    @Test
    fun theCompactBarIs4dpTheExpandedBar8dpAndReduceMotionStillMoves() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.level(0.1f), height = 8.dp, reduceMotion = true)
        onNodeWithTag(METER).assertHeightIsEqualTo(8.dp)
        mainClock.advanceTimeByFrame()
        val filled = columnsOf(body).size
        val expected = (trackWidthPx() * 2 / 3f).toInt()
        assertTrue(abs(filled - expected) <= tolerancePx(), "reduce motion shows the level at once: $filled px, expected about $expected")
    }

    @Test
    fun theDefaultHeightIsTheCompact4dpAndNothingComposesWhileDisarmed() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.silent())
        onNodeWithTag(METER).assertHeightIsEqualTo(4.dp)
    }

    @Test
    fun nothingComposesWhileDisarmed() = runComposeUiTest {
        setMeter(ScriptedInputTelemetry.silent(), armed = false)
        onNodeWithTag(METER).assertDoesNotExist()
    }

    private companion object {
        const val METER = "test.meter"
        const val CLIP = "test.meter.clip"
        val WIDTH = 300.dp
    }
}
