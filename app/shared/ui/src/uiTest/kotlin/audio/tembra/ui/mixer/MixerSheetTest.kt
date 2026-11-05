package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.shortcuts.LocalOverlayStack
import audio.tembra.ui.shortcuts.OverlayStack
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mixer sheet over a lanes box (feature 012, contracts/mixer-ui.md "MixerSheet"; research R1; T045): it opens at
 * the half height with the lanes visible above, three strips and the pinned master fit 360 dp, a drag on the handle
 * moves it between the two heights, a swipe down from the half height closes it, the shell's close-overlay action (the
 * Escape key's route after 011) closes it, landscape has one anchor, and a drag on a fader moves the fader, not the
 * sheet. The state is held as the ViewModel would hold it: the callbacks that change the height or close the sheet
 * write it back. The per-frame meter read lands in User Story 2 (T059 extends this file).
 */
@OptIn(ExperimentalTestApi::class)
class MixerSheetTest {

    private val calls = mutableListOf<String>()
    private val stack = OverlayStack()
    private val ui = mutableStateOf(MixerUi())
    private var densityScale = 1f
    private var pageMargin = 0.dp

    private val callbacks = TimelineCallbacks.None.copy(
        onMixerToggle = {
            calls += "toggle"
            ui.value = ui.value.copy(open = !ui.value.open)
        },
        onMixerHeight = { height ->
            calls += "height($height)"
            ui.value = ui.value.copy(height = height, form = StripForm.of(height, ui.value.anchors.heightOf(height).value))
        },
        onGain = { id, gain -> calls += "gain(${id.value},${gain.readout})" },
        onGainSettled = { calls += "settled" },
    )

    private fun strips(count: Int) = List(count) { index ->
        StripUi(
            id = TrackId("t${index + 1}"), index = index, number = index + 1, name = "Track ${index + 1}", colorIndex = index,
            gainDb = GainDb.Unity, pan = Pan.Centre, muted = false, soloed = false, audible = true,
        )
    }.toPersistentList()

    /** The sheet inside a lanes box of [width] × [lanes] under [layout], opened at [height]; a 40 dp marker at the top stands for the lanes. */
    private fun ComposeUiTest.setSheet(layout: TimelineLayout = TimelineLayout.Compact, width: Dp = 360.dp, lanes: Dp = 500.dp, height: MixerHeight = MixerHeight.Half, strips: Int = 3) {
        val anchors = MixerAnchors.of(layout, lanes)
        ui.value = MixerUi(open = true, height = height, form = StripForm.of(height, anchors.heightOf(height).value), anchors = anchors, strips = strips(strips))
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalOverlayStack provides stack) {
                    densityScale = LocalDensity.current.density
                    pageMargin = TembraTheme.spacing.pageMargin
                    Box(Modifier.requiredWidth(width).requiredHeight(lanes)) {
                        Box(Modifier.fillMaxWidth().height(LANES_MARKER_HEIGHT).align(Alignment.TopCenter).testTag(LANES_MARKER))
                        MixerSheet(ui = ui.value, callbacks = callbacks, lanesHeight = lanes)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 1f, "$what is $actual, expected $expected")

    @Test
    fun opensAtHalfWithTheLanesVisibleAbove() = runComposeUiTest {
        setSheet()
        // Compact at 500 dp of lanes: 45 % is 225 dp, under the 256 dp floor, so the half sheet is 256 dp tall.
        val sheet = onNodeWithTag(MixerTestTags.SHEET).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertNear(HALF_TOP, sheet.top, "the sheet's top")
        assertNear(500.dp, sheet.bottom, "the sheet's bottom")
        onNodeWithTag(LANES_MARKER).assertIsDisplayed()
        onNodeWithTag(MixerTestTags.HANDLE).assertIsDisplayed()
        onNodeWithText(MixerCopy.mixer).assertIsDisplayed()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun threeStripsAndTheMasterFitThreeHundredSixtyWithTheMasterPinnedRight() = runComposeUiTest {
        setSheet(strips = 8)
        for (index in 0 until 3) onNodeWithTag(MixerTestTags.strip(index)).assertIsDisplayed()
        val master = onNodeWithTag(MixerTestTags.MASTER).assertIsDisplayed().getUnclippedBoundsInRoot()
        val row = onNodeWithTag(MixerTestTags.STRIPS).getUnclippedBoundsInRoot()
        assertTrue(row.right <= master.left + 1.dp, "the strip row $row runs under the master $master")
        assertTrue(master.right >= 360.dp - pageMargin - 1.dp, "the master $master is not pinned at the right edge")
        onNodeWithTag(MixerTestTags.MASTER_FADER).assertIsDisplayed()
    }

    @Test
    fun aDragOnTheHandleMovesBetweenTheHeights() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f * densityScale) }
        waitForIdle()
        assertEquals(listOf("height(Full)"), calls)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the full sheet's top")
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 150f * densityScale) }
        waitForIdle()
        assertEquals(listOf("height(Full)", "height(Half)"), calls)
        assertNear(HALF_TOP, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the half sheet's top")
    }

    @Test
    fun aSwipeDownFromHalfCloses() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 260f * densityScale) }
        waitForIdle()
        assertEquals(listOf("toggle"), calls)
        assertFalse(ui.value.open)
        onNodeWithTag(MixerTestTags.SHEET).assertDoesNotExist()
    }

    @Test
    fun theShellsCloseOverlayActionClosesTheSheet() = runComposeUiTest {
        setSheet()
        assertTrue(stack.closeTop(), "the open sheet registered as an overlay")
        waitForIdle()
        assertEquals(listOf("toggle"), calls)
        onNodeWithTag(MixerTestTags.SHEET).assertDoesNotExist()
        assertFalse(stack.closeTop(), "the closed sheet left the overlay stack")
    }

    @Test
    fun landscapeHasTheOneFullAnchor() = runComposeUiTest {
        setSheet(layout = TimelineLayout.Landscape, width = 700.dp, lanes = 300.dp, height = MixerHeight.Full)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the sheet's top")
        onNodeWithTag(LANES_MARKER).assertIsDisplayed()
        // A short, slow drag down has no half anchor to land on: the sheet returns to the full height, nothing is reported.
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 60f * densityScale, durationMillis = 1_000) }
        waitForIdle()
        assertEquals(emptyList(), calls)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the sheet's top after the short drag")
    }

    @Test
    fun aDragOnAFaderMovesTheFaderNotTheSheet() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.fader(1)).performTouchInput { swipeUp() }
        waitForIdle()
        assertTrue(calls.any { it.startsWith("gain(t2,") }, "the fader did not move: $calls")
        assertTrue(calls.none { it.startsWith("height") || it == "toggle" }, "the sheet moved under the fader drag: $calls")
        assertEquals("settled", calls.last())
    }

    private companion object {
        const val LANES_MARKER = "lanes.top"
        val LANES_MARKER_HEIGHT = 40.dp

        /** 500 dp of lanes less the 256 dp half sheet. */
        val HALF_TOP = 244.dp
    }
}
