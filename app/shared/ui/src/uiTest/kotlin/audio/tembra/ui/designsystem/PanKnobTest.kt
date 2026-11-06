package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import audio.tembra.core.project.Pan
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The pan knob (feature 012, contracts/mixer-ui.md "PanKnob"; T067): a vertical drag of 2 dp per percent, a release
 * within 5 % settling on the centre, a double tap to the centre, the wheel, the keys, the range semantics in percent,
 * the "L 35" readout, hover and focus through an injected interaction source.
 */
@OptIn(ExperimentalTestApi::class)
class PanKnobTest {

    private val changes = mutableListOf<Pan>()
    private var settled = 0
    private var slopPx = 0f
    private var pixelDensity: Density = Density(1f)
    private var primary = Color.Unspecified

    private fun ComposeUiTest.setKnob(initial: Pan, source: MutableInteractionSource? = null) {
        setContent {
            TembraTheme {
                slopPx = LocalViewConfiguration.current.touchSlop
                pixelDensity = LocalDensity.current
                primary = TembraTheme.colors.primary
                var pan by remember { mutableStateOf(initial) }
                PanKnob(
                    pan = pan,
                    onPan = {
                        pan = it
                        changes += it
                    },
                    onSettled = { settled++ },
                    description = DESCRIPTION,
                    interactionSource = source,
                    testTag = TAG,
                )
            }
        }
    }

    private fun percentPx(percent: Int): Float = with(pixelDensity) { (PanKnobDefaults.DpPerPercent * percent).toPx() }

    @Test
    fun readsItsValueWithRangeSemanticsInPercent() = runComposeUiTest {
        setKnob(Pan(-35))
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals(DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "L 35"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(-35f, -100f..100f, 0)))
        onNodeWithTag(TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertEquals(listOf(Pan(20)), changes)
        assertEquals(1, settled)
    }

    @Test
    fun aVerticalDragTurnsTwoDpPerPercentAndKeepsFollowingOffTheDial() = runComposeUiTest {
        setKnob(Pan.Centre)
        onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -(slopPx + percentPx(30))))
        }
        assertEquals(Pan(30), changes.last(), "30 % to the right for 60 dp upward past the slop")
        onNodeWithTag(TAG).performTouchInput {
            moveBy(Offset(200f, 0f))
            moveBy(Offset(0f, -percentPx(10)))
            up()
        }
        assertEquals(Pan(40), changes.last(), "the turn kept following off the dial")
        assertEquals(1, settled)
    }

    @Test
    fun aReleaseWithinFivePercentSettlesOnTheCentre() = runComposeUiTest {
        setKnob(Pan(-20))
        onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -(slopPx + percentPx(17))))
            up()
        }
        assertEquals(Pan.Centre, changes.last(), "L 3 on release snaps to C: $changes")
        assertEquals(1, settled)
    }

    @Test
    fun aDoubleTapReturnsToTheCentre() = runComposeUiTest {
        setKnob(Pan(60))
        onNodeWithTag(TAG).performTouchInput { doubleClick() }
        assertEquals(listOf(Pan.Centre), changes)
        assertEquals(1, settled)
        onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "C"))
    }

    @Test
    fun theWheelTurnsOnePercentPerNotch() = runComposeUiTest {
        setKnob(Pan.Centre)
        onNodeWithTag(TAG).performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        onNodeWithTag(TAG).performMouseInput { scroll(1f) }
        onNodeWithTag(TAG).performMouseInput { scroll(1f) }
        assertEquals(listOf(Pan(1), Pan.Centre, Pan(-1)), changes)
        assertEquals(3, settled)
    }

    @Test
    fun arrowsPageAndHomeTurnTheKnob() = runComposeUiTest {
        setKnob(Pan.Centre)
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(listOf(1, 0, -1, 9, -1, -11, 0), changes.map { it.percent })
        assertEquals(7, settled)
    }

    @Test
    fun theEndsAreHardLeftAndHardRight() = runComposeUiTest {
        setKnob(Pan(95))
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        assertEquals(listOf(Pan(100)), changes)
        onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "R 100"))
    }

    @Test
    fun hoverTintsTheDialAndFocusDrawsTheRing() = runComposeUiTest {
        val source = MutableInteractionSource()
        setKnob(Pan.Centre, source = source)
        // A point on the dial's face, inside the arc and clear of the pointer at the top.
        val face = DpOffset(PanKnobDefaults.Size / 2 - 6.dp, PanKnobDefaults.Size / 2 + 4.dp)
        val plain = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), face, pixelDensity, radius = 0)
        runBlocking { source.emit(HoverInteraction.Enter()) }
        waitForIdle()
        val hovered = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), face, pixelDensity, radius = 0)
        assertNotEquals(plain, hovered, "the hover tint did not change the dial")
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).assertIsFocused()
        val edge = DpOffset(1.dp, PanKnobDefaults.Size / 2)
        val ring = onNodeWithTag(TAG).averageColorAt(onNodeWithTag(TAG).captureToImage(), edge, pixelDensity, radius = 0)
        assertTrue(abs(ring.red - primary.red) < 0.12f && abs(ring.green - primary.green) < 0.12f && abs(ring.blue - primary.blue) < 0.12f, "the focus ring reads $ring, not the primary $primary")
    }

    private companion object {
        const val TAG = "pan"
        const val DESCRIPTION = "Drums, pan"
    }
}
