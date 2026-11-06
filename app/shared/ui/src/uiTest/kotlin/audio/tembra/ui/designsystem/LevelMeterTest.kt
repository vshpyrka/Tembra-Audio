package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.LocalDawColors
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip's two-bar level meter (feature 012, spec FR-012; contracts/mixer-ui.md "LevelMeter"; T057): silence, −20 dB,
 * full scale with the latch and its reset, the hold mark under a frozen clock, the master's index, reduce motion, the
 * semantics; semantics before pixels, the pixels read against opaque stand-ins for the meter's tokens. The shared
 * snapshot is written by the test as the sheet root would write it each frame.
 */
@OptIn(ExperimentalTestApi::class)
class LevelMeterTest {

    private val body = Color(0xFF00FF00)
    private val warning = Color(0xFFFFFF00)
    private val clip = Color(0xFFFF00FF)
    private val meterColors = DawColors.Default.copy(meterBody = body, meterWarning = warning, meterClip = clip)
    private val levels = MixSnapshot()
    private val level = mutableStateOf(MeterLevel.Silent)
    private var resets = 0
    private var mark = Color.Unspecified

    private fun ComposeUiTest.setMeter(index: Int = 0, reduceMotion: Boolean = false) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                mark = TembraTheme.colors.onSurface
                CompositionLocalProvider(
                    LocalDawColors provides meterColors,
                    LocalTembraMotion provides TembraTheme.motion.copy(reduceMotion = reduceMotion),
                ) {
                    Box(Modifier.height(HEIGHT)) {
                        LevelMeter(
                            reading = MeterReading.playback(levels, index),
                            tick = rememberFrameTick(active = true),
                            level = level,
                            onResetClip = { resets += 1 },
                            modifier = Modifier.fillMaxHeight(),
                            testTag = METER,
                            clipTestTag = CLIP,
                        )
                    }
                }
            }
        }
    }

    private fun set(index: Int, peakLinear: Float, clipped: Boolean = false) {
        levels.trackCount = index + 1
        levels.peak[index * 2] = peakLinear
        levels.peak[index * 2 + 1] = peakLinear
        levels.clipMask = if (clipped) levels.clipMask or (1L shl index) else levels.clipMask and (1L shl index).inv()
    }

    /** The y of every pixel of [color] down the left bar's centre column. */
    private fun ComposeUiTest.rowsOf(color: Color): List<Int> {
        val pixels = onNodeWithTag(METER).captureToImage().toPixelMap()
        val x = with(density) { (LevelMeterDefaults.BarWidth / 2).roundToPx() }
        return (0 until pixels.height).filter { y -> close(pixels[x, y], color) }
    }

    private fun ComposeUiTest.barsTopPx(): Int = with(density) { (LevelMeterDefaults.Width + LevelMeterDefaults.Gap).roundToPx() }

    private fun ComposeUiTest.barsHeightPx(): Int = with(density) { HEIGHT.roundToPx() } - barsTopPx()

    /** The rounded ends cost up to the bar's width in pixels at any density. */
    private fun ComposeUiTest.tolerancePx(): Int = with(density) { LevelMeterDefaults.BarWidth.roundToPx() } + 2

    private fun close(a: Color, b: Color): Boolean = abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f

    @Test
    fun silenceDrawsNothingAndAnnouncesTheFloor() = runComposeUiTest {
        setMeter()
        mainClock.advanceTimeByFrame()
        onNodeWithTag(METER).assertContentDescriptionEquals(LevelMeterCopy.level).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−60 dB"))
        assertEquals(emptyList(), rowsOf(body))
        onNodeWithTag(CLIP).assertContentDescriptionEquals(LevelMeterCopy.clipIndicator).assertHasNoClickAction()
    }

    @Test
    fun minusTwentyDecibelsFillsTwoThirdsOfTheBar() = runComposeUiTest {
        set(0, 0.1f)
        level.value = MeterLevel(peakDb = -20f, rmsDb = -23f, clipped = false)
        setMeter()
        mainClock.advanceTimeBy(200)
        val filled = rowsOf(body).size
        val expected = (barsHeightPx() * 2 / 3f).toInt()
        assertTrue(abs(filled - expected) <= tolerancePx(), "filled $filled px, expected about $expected")
        assertEquals(emptyList(), rowsOf(warning))
        onNodeWithTag(METER).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−20 dB"))
    }

    @Test
    fun fullScaleLightsTheWarningAndTheClipSquareAndATapResets() = runComposeUiTest {
        set(0, 1f, clipped = true)
        level.value = MeterLevel(peakDb = 0f, rmsDb = -3f, clipped = true)
        setMeter()
        mainClock.advanceTimeBy(200)
        assertTrue(rowsOf(warning).isNotEmpty(), "the warning colour above −6 dB")
        val square = onNodeWithTag(METER).captureToImage().toPixelMap()
        val half = with(density) { (LevelMeterDefaults.Width / 2).roundToPx() }
        assertTrue(close(square[half, half], clip), "the clip square lit: ${square[half, half]}")
        onNodeWithTag(CLIP).assertContentDescriptionEquals(LevelMeterCopy.clipped).assertHasClickAction().performClick()
        assertEquals(1, resets)
        onNodeWithTag(METER).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 dB"))
    }

    @Test
    fun theHoldMarkStaysOneAndAHalfSecondsThenFalls() = runComposeUiTest {
        set(0, 0.5f)
        setMeter()
        mainClock.advanceTimeBy(200)
        set(0, 0.01f)
        mainClock.advanceTimeBy(800)
        val held = rowsOf(mark)
        val top = barsTopPx()
        val bars = barsHeightPx()
        assertTrue(held.isNotEmpty() && held.min() <= top + bars * 0.15f, "the mark near −6 dB after 1 s: $held in $top..${top + bars}")
        mainClock.advanceTimeBy(2_000)
        val fallen = rowsOf(mark)
        assertTrue(fallen.isEmpty() || fallen.min() > top + bars * 0.4f, "the mark fallen after 3 s: $fallen in $top..${top + bars}")
    }

    @Test
    fun theMasterIndexReadsTheMasterFields() = runComposeUiTest {
        levels.trackCount = 2
        levels.masterPeak[0] = 0.1f
        levels.masterPeak[1] = 0.1f
        setMeter(index = MixSnapshot.MASTER_INDEX)
        mainClock.advanceTimeBy(200)
        val filled = rowsOf(body).size
        val expected = (barsHeightPx() * 2 / 3f).toInt()
        assertTrue(abs(filled - expected) <= tolerancePx(), "the master meter filled $filled px, expected about $expected")
    }

    @Test
    fun underReduceMotionTheLevelShowsAtOnce() = runComposeUiTest {
        set(0, 0.1f)
        setMeter(reduceMotion = true)
        mainClock.advanceTimeByFrame()
        val filled = rowsOf(body).size
        val expected = (barsHeightPx() * 2 / 3f).toInt()
        assertTrue(abs(filled - expected) <= tolerancePx(), "reduce motion shows the level at once: $filled px, expected about $expected")
    }

    private companion object {
        const val METER = "test.meter"
        const val CLIP = "test.meter.clip"
        val HEIGHT = 200.dp
    }
}
