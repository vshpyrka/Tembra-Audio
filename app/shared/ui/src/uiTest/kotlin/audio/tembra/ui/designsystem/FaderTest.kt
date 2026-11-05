package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.FaderTaper
import audio.tembra.core.project.GainDb
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The fader (feature 012, contracts/mixer-ui.md "Fader"; T044): a drag moves the value 1:1 in throw space through the
 * console taper and keeps following off the rail, a double tap animates back to unity (a jump under reduce motion),
 * `onSettled` fires once per gesture, the wheel and the keys step the value, the semantics work in the value domain,
 * hover and focus draw their tints (through an injected interaction source), and the percent scale steps whole percent.
 */
@OptIn(ExperimentalTestApi::class)
class FaderTest {

    private val changes = mutableListOf<Float>()
    private var settled = 0
    private var slopPx = 0f
    private var pixelDensity: Density = Density(1f)
    private var primary = Color.Unspecified

    private fun ComposeUiTest.setFader(
        initial: Float,
        scale: FaderScale = FaderScale.Gain,
        source: MutableInteractionSource? = null,
        reduceMotion: Boolean = false,
    ) {
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = reduceMotion)) {
                    slopPx = LocalViewConfiguration.current.touchSlop
                    pixelDensity = LocalDensity.current
                    primary = TembraTheme.colors.primary
                    var value by remember { mutableStateOf(initial) }
                    Fader(
                        value = value,
                        onValue = {
                            value = it
                            changes += it
                        },
                        onSettled = { settled++ },
                        description = DESCRIPTION,
                        readout = readoutOf(scale, value),
                        modifier = Modifier.width(FaderDefaults.HandleWidth).height(HEIGHT_DP.dp),
                        scale = scale,
                        interactionSource = source,
                        testTag = TAG,
                    )
                }
            }
        }
    }

    private fun readoutOf(scale: FaderScale, value: Float): String = when (scale) {
        FaderScale.Gain -> FaderScale.Gain.gainOf(value).readout
        FaderScale.Percent -> "${value.toInt()} percent"
    }

    /** The throw in pixels: the rail's height less the handle's. */
    private fun throwPx(): Float = with(pixelDensity) { (HEIGHT_DP.dp - FaderDefaults.HandleHeight).toPx() }

    /** The handle's centre for a throw position, in dp from the fader's top. */
    private fun handleCentreDp(throwPosition: Float): Float = with(pixelDensity) {
        (FaderDefaults.HandleHeight / 2 + (HEIGHT_DP.dp - FaderDefaults.HandleHeight) * (1f - throwPosition)).value
    }

    @Test
    fun readsItsValueWithRangeSemanticsInDecibels() = runComposeUiTest {
        setFader(-12f)
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals(DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−12.0dB"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(-12f, FaderScale.Gain.range, 0)))
        onNodeWithTag(TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
        assertEquals(listOf(-6f), changes)
        assertEquals(1, settled, "a screen-reader step is a settled gesture")
    }

    @Test
    fun aDragMovesTheValueOneToOneInThrowSpace() = runComposeUiTest {
        setFader(0f)
        val throwPx = throwPx()
        // Unity sits at 0.7 of the throw; a fifth of the throw upward (after the slop) lands at 0.9: +4 dB on the taper.
        onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -(slopPx + 0.2f * throwPx)))
            up()
        }
        val expected = FaderTaper.dbOf(0.9f).value
        assertTrue(changes.isNotEmpty(), "the drag produced no value")
        assertTrue(abs(changes.last() - expected) < 0.2f, "the drag landed at ${changes.last()} dB, not $expected")
        assertEquals(1, settled, "one gesture settles once")
    }

    @Test
    fun theDragKeepsFollowingWhenThePointerLeavesTheRailSideways() = runComposeUiTest {
        setFader(-12f)
        val throwPx = throwPx()
        onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -(slopPx + 0.05f * throwPx)))
        }
        val beforeLeaving = changes.last()
        onNodeWithTag(TAG).performTouchInput {
            moveBy(Offset(300f, 0f))
            moveBy(Offset(0f, -0.1f * throwPx))
            up()
        }
        assertTrue(changes.last() > beforeLeaving, "the value stopped following off the rail: $beforeLeaving then ${changes.last()}")
        assertEquals(1, settled)
    }

    @Test
    fun aDoubleTapAnimatesBackToUnityAndSettlesOnce() = runComposeUiTest {
        setFader(-12f)
        onNodeWithTag(TAG).performTouchInput { doubleClick() }
        waitForIdle()
        assertEquals(0f, changes.last(), "the double tap returns to unity")
        assertTrue(changes.size > 1, "the return is animated through intermediate values: $changes")
        assertEquals(1, settled)
    }

    @Test
    fun underReduceMotionTheDoubleTapJumps() = runComposeUiTest {
        setFader(-12f, reduceMotion = true)
        onNodeWithTag(TAG).performTouchInput { doubleClick() }
        waitForIdle()
        assertEquals(listOf(0f), changes, "one jump, no animation")
        assertEquals(1, settled)
    }

    @Test
    fun theWheelStepsHalfADecibelPerNotch() = runComposeUiTest {
        setFader(0f)
        onNodeWithTag(TAG).performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        onNodeWithTag(TAG).performMouseInput { scroll(1f) }
        onNodeWithTag(TAG).performMouseInput { scroll(1f) }
        assertEquals(listOf(0.5f, 0f, -0.5f), changes)
        assertEquals(3, settled, "every notch is a gesture of its own")
    }

    @Test
    fun arrowsPageAndHomeMoveTheValue() = runComposeUiTest {
        setFader(0f)
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(listOf(0.5f, 0f, -0.5f, 5.5f, -0.5f, 0f), changes)
        assertEquals(6, settled)
    }

    @Test
    fun theBottomOfTheThrowIsSilence() = runComposeUiTest {
        setFader(-58f)
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        assertEquals(listOf(FaderScale.Gain.range.start), changes)
        onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−∞"))
        assertEquals(GainDb.Silence, FaderScale.Gain.gainOf(changes.last()))
    }

    @Test
    fun hoverTintsTheHandleAndFocusDrawsTheRing() = runComposeUiTest {
        val source = MutableInteractionSource()
        setFader(0f, source = source)
        val centreY = handleCentreDp(FaderTaper.throwOf(GainDb.Unity)).dp
        // A point inside the handle's upper half, clear of the centre line drawn through its middle.
        val handle = DpOffset(FaderDefaults.HandleWidth / 2, centreY - 8.dp)
        val plain = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), handle, pixelDensity)
        runBlocking { source.emit(HoverInteraction.Enter()) }
        waitForIdle()
        val hovered = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), handle, pixelDensity)
        assertNotEquals(plain, hovered, "the hover tint did not change the handle")
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).assertIsFocused()
        val edge = DpOffset(1.dp, centreY)
        val ring = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), edge, pixelDensity, radius = 0)
        assertTrue(ring.near(primary), "the focus ring at the handle's edge reads $ring, not the primary $primary")
    }

    @Test
    fun thePercentScaleStepsWholePercentAndPagesTen() = runComposeUiTest {
        setFader(80f, scale = FaderScale.Percent)
        onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 0)))
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(listOf(81f, 71f, 81f, 91f, 100f, 80f), changes)
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    private companion object {
        const val TAG = "fader"
        const val DESCRIPTION = "Drums, gain"
        const val HEIGHT_DP = 300
    }
}
