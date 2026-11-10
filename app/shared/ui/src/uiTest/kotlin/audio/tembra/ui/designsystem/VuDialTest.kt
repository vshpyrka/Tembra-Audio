package audio.tembra.ui.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.LocalDawColors
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip's VU dial (feature 012, spec FR-013; contracts/mixer-ui.md "VuDial"; T058): −18 dBFS reads 0 VU, −38 pins
 * at −20, −15 at +3 with the warning arc, the needle moves smoothly under the frame clock and jumps under reduce motion;
 * semantics before pixels, the needle found by its colour.
 */
@OptIn(ExperimentalTestApi::class)
class VuDialTest {

    private val warning = Color(0xFFFFFF00)
    private val levels = MixSnapshot()
    private val level = mutableStateOf(MeterLevel.Silent)
    private var needle = Color.Unspecified

    private fun ComposeUiTest.setDial(reduceMotion: Boolean = false) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                needle = TembraTheme.colors.onSurface
                CompositionLocalProvider(
                    LocalDawColors provides DawColors.Default.copy(meterWarning = warning),
                    LocalTembraMotion provides TembraTheme.motion.copy(reduceMotion = reduceMotion),
                ) {
                    VuDial(levels = levels, index = 0, tick = rememberFrameTick(active = true), level = level, testTag = DIAL)
                }
            }
        }
    }

    private fun rms(db: Float) {
        levels.trackCount = 1
        val linear = 10f.pow(db / 20f)
        levels.rms[0] = linear
        levels.rms[1] = linear
        level.value = MeterLevel(peakDb = db, rmsDb = db, clipped = false)
    }

    /** The x of every pixel of the needle's colour over the dial's upper two thirds (the pivot sits at the bottom). */
    private fun ComposeUiTest.needleColumns(): List<Int> {
        val pixels = onNodeWithTag(DIAL).captureToImage().toPixelMap()
        val columns = mutableListOf<Int>()
        for (y in 0 until pixels.height * 2 / 3) for (x in 0 until pixels.width) if (close(pixels[x, y], needle)) columns += x
        return columns
    }

    private fun ComposeUiTest.widthPx(): Int = onNodeWithTag(DIAL).captureToImage().width

    private fun ComposeUiTest.hasWarning(): Boolean {
        val pixels = onNodeWithTag(DIAL).captureToImage().toPixelMap()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (close(pixels[x, y], warning)) return true
        return false
    }

    private fun close(a: Color, b: Color): Boolean = abs(a.red - b.red) < 0.1f && abs(a.green - b.green) < 0.1f && abs(a.blue - b.blue) < 0.1f

    @Test
    fun theReferenceLevelReadsZeroVu() = runComposeUiTest {
        rms(-18f)
        setDial()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(DIAL).assertContentDescriptionEquals(LevelMeterCopy.vu).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 VU"))
        onNodeWithTag(DIAL).assertWidthIsEqualTo(VuDialWidth).assertHeightIsEqualTo(VuDialHeight)
        val columns = needleColumns()
        assertTrue(columns.isNotEmpty(), "no needle drawn")
        val width = widthPx()
        assertTrue(columns.max() > width / 2 && columns.max() < width * 0.95f, "0 VU sits right of the middle, short of the end: ${columns.max()} of $width")
    }

    @Test
    fun farBelowTheReferencePinsAtMinusTwenty() = runComposeUiTest {
        rms(-38f)
        setDial()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(DIAL).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−20 VU"))
        val columns = needleColumns()
        assertTrue(columns.isNotEmpty() && columns.min() < widthPx() / 4, "the needle at the left end: ${columns.minOrNull()} of ${widthPx()}")
        assertEquals("−20 VU", VuScale.announce(VuScale.vuOf(-60f)), "anything lower pins too")
    }

    @Test
    fun aHotSignalPinsAtPlusThreeOverTheWarningArc() = runComposeUiTest {
        rms(-15f)
        setDial()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(DIAL).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "+3 VU"))
        assertTrue(hasWarning(), "the arc above 0 VU in the warning colour")
        val columns = needleColumns()
        assertTrue(columns.isNotEmpty() && columns.max() > widthPx() * 3 / 4, "the needle at the right end: ${columns.maxOrNull()} of ${widthPx()}")
    }

    @Test
    fun theNeedleMovesSmoothlyUnderTheFrameClockAndJumpsUnderReduceMotion() = runComposeUiTest {
        rms(-38f)
        setDial()
        mainClock.advanceTimeBy(500)
        val atRest = needleColumns().max()
        rms(-15f)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val moving = needleColumns().max()
        assertTrue(moving > atRest, "the needle set off: $atRest then $moving")
        assertTrue(moving < widthPx() * 3 / 4, "two frames in, the needle is not yet at the end: $moving of ${widthPx()}")
        mainClock.advanceTimeBy(500)
        assertTrue(needleColumns().max() > moving, "and it kept going: ${needleColumns().max()}")
    }

    @Test
    fun underReduceMotionTheNeedleJumps() = runComposeUiTest {
        rms(-38f)
        setDial(reduceMotion = true)
        mainClock.advanceTimeByFrame()
        rms(-15f)
        mainClock.advanceTimeByFrame()
        assertTrue(needleColumns().max() > widthPx() * 3 / 4, "the needle at the end after one frame: ${needleColumns().max()} of ${widthPx()}")
    }

    private companion object {
        const val DIAL = "test.vu"
    }
}
