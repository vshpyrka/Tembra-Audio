package audio.tembra.ui.transport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone bar's master volume pop-up on its own (spec FR-007, D1 2026-10-26; T138), anchored to a 44 dp box at the
 * window's bottom start as the speaker button sits in the bar: the 200 dp slider and its percentage, the semantics, the
 * placement above the anchor, and the dismiss requests from Escape and a tap outside.
 */
@OptIn(ExperimentalTestApi::class)
class MasterVolumePopupTest {

    private val volumes = mutableListOf<Int>()
    private var dismissed = 0

    /** [percent] is held as state: a volume reported through `onVolume` is shown back, as the screen's state does. */
    private fun ComposeUiTest.setPopup(percent: Int = 80, onPercent: ((Int) -> Unit) -> Unit = {}) {
        setContent {
            TembraTheme {
                var current by remember { mutableStateOf(percent) }
                onPercent { current = it }
                Box(Modifier.fillMaxSize().testTag(WINDOW), contentAlignment = Alignment.BottomStart) {
                    Box(Modifier.padding(start = 4.dp, bottom = 10.dp).size(44.dp).testTag(ANCHOR)) {
                        MasterVolumePopup(
                            percent = current,
                            onVolume = {
                                volumes += it
                                current = it
                            },
                            onDismiss = { dismissed++ },
                            clearance = CLEARANCE,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theCardHoldsTheSliderAt200dpAndItsPercentAsText() = runComposeUiTest {
        setPopup()
        val slot = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        assertTrue(slot.isSliderSlotOf(200.dp), "the slider $slot is not 200 dp wide")
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("80%")
        val card = screenBounds(MASTER_POPUP_TAG)
        val slider = screenBounds(TAG)
        val percent = screenBounds(MASTER_PERCENT_TAG)
        assertTrue(percent.left >= slider.right, "the percentage $percent is not right of the slider $slider")
        assertTrue(card.contains(slider) && card.contains(percent), "the card $card does not hold the slider $slider and the percentage $percent")
    }

    @Test
    fun theSliderReadsTheMasterVolumeAndThePercentIsNotReadTwice() = runComposeUiTest {
        setPopup()
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals(TransportCopy.masterVolume)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 99)))
        // The slider's state already says "80 percent"; the visible "80%" is not a second stop for a screen reader.
        onNodeWithTag(MASTER_PERCENT_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        onNodeWithTag(MASTER_POPUP_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, TransportCopy.masterVolume))
    }

    /** Above the anchor by the clearance it is given (the bar's top), never over it, and inside the window. */
    @Test
    fun theCardSitsAboveItsAnchorAndInsideTheWindow() = runComposeUiTest {
        setPopup()
        val card = screenBounds(MASTER_POPUP_TAG)
        val anchor = screenBounds(ANCHOR)
        val window = screenBounds(WINDOW)
        val clearance = with(density) { CLEARANCE.toPx() }
        assertTrue(card.bottom <= anchor.top - clearance + 1f, "the card $card is not $CLEARANCE above the anchor $anchor")
        assertTrue(card.left >= window.left && card.right <= window.right && card.top >= window.top, "the card $card leaves the window $window")
    }

    @Test
    fun keysMoveTheVolumeAndThePercentFollows() = runComposeUiTest {
        setPopup()
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("81%")
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("91%")
        assertEquals(listOf(81, 91), volumes)
    }

    /** One digit or three, the label keeps its width, so the slider beside it never shifts. */
    @Test
    fun thePercentKeepsItsWidthFromOneDigitToThree() = runComposeUiTest {
        var update: (Int) -> Unit = {}
        setPopup(percent = 5) { update = it }
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("5%")
        val narrow = screenBounds(MASTER_PERCENT_TAG).width
        update(100)
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("100%")
        assertEquals(narrow, screenBounds(MASTER_PERCENT_TAG).width, "the label changed its width with its digits")
    }

    @Test
    fun escapeAndATapOutsideAskToDismiss() = runComposeUiTest {
        setPopup()
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissed, "Escape did not ask to dismiss")
        // The window's centre is well above the card at the bottom start.
        tapThroughTheWindow(onNodeWithTag(WINDOW))
        assertEquals(2, dismissed, "a tap outside did not ask to dismiss")
        assertEquals(emptyList(), volumes)
    }

    private fun ComposeUiTest.screenBounds(tag: String): Rect {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        return Rect(node.positionOnScreen, node.size.toSize())
    }

    private fun Rect.contains(other: Rect): Boolean =
        other.left >= left - 0.5f && other.right <= right + 0.5f && other.top >= top - 0.5f && other.bottom <= bottom + 0.5f

    private companion object {
        const val WINDOW = "test.window"
        const val ANCHOR = "test.anchor"
        /** The phone bar's: half of 64 − 44 dp from the button's top to the bar's, plus an 8 dp gap. */
        val CLEARANCE = 18.dp
    }
}
