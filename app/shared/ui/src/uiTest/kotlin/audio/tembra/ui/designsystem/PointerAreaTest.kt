package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportButton
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pointer areas match what is drawn (feature 011, spec FR-016a, research R20): for every design-system interactive
 * component, hovering changes the drawing at the edges of its whole clickable area, not only inside a smaller drawn
 * shape, so the area that shows the hover state is the area that responds to a click. Checked at the four edge
 * midpoints of the clickable node, two pixels in.
 */
@OptIn(ExperimentalTestApi::class)
class PointerAreaTest {

    private fun ComposeUiTest.assertHoverCoversTheClickableArea(tag: String, hover: ComposeUiTest.() -> Unit, content: @Composable () -> Unit) {
        mainClock.autoAdvance = false
        setContent { TembraTheme { Box(Modifier.padding(24.dp)) { content() } } }
        mainClock.advanceTimeBy(500)
        val resting = onNodeWithTag(tag).captureToImage()
        hover()
        mainClock.advanceTimeBy(500)
        val hovered = onNodeWithTag(tag).captureToImage()
        val unchanged = edgeMidpoints(resting).filter { (x, y) -> resting.averageColorAt(x, y, 0).approximately(hovered.averageColorAt(x, y, 0), 0.01f) }
        assertTrue(unchanged.isEmpty(), "$tag: hovering leaves its clickable edges unchanged at $unchanged (${resting.width}×${resting.height} px)")
    }

    private fun edgeMidpoints(image: ImageBitmap): List<Pair<Int, Int>> = listOf(
        image.width / 2 to 2,
        image.width / 2 to image.height - 3,
        2 to image.height / 2,
        image.width - 3 to image.height / 2,
    )

    private fun ComposeUiTest.hoverWith(source: MutableInteractionSource): ComposeUiTest.() -> Unit = {
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
    }

    @Test
    fun aPill() = runComposeUiTest {
        val source = MutableInteractionSource()
        assertHoverCoversTheClickableArea("pill", hoverWith(source)) {
            TembraPill(text = "Snap 1/16", selected = false, onClick = {}, interactionSource = source, testTag = "pill")
        }
    }

    @Test
    fun aSegment() = runComposeUiTest {
        val source = MutableInteractionSource()
        assertHoverCoversTheClickableArea("seg.b", hoverWith(source)) {
            TembraSegmentedControl(
                items = listOf(SegmentSpec("a", "Guitar"), SegmentSpec("b", "Bass", interactionSource = source)),
                selectedId = "a", onSelect = {}, style = SegmentedStyle.Small, testTag = "seg",
            )
        }
    }

    @Test
    fun aChip() = runComposeUiTest {
        val source = MutableInteractionSource()
        assertHoverCoversTheClickableArea("chips.b", hoverWith(source)) {
            TembraChipRow(
                items = listOf(ChipSpec("a", "4/4"), ChipSpec("b", "3/4", interactionSource = source)),
                selectedId = "a", onSelect = {}, accent = ChipAccent.Secondary, testTag = "chips",
            )
        }
    }

    @Test
    fun aToggle() = runComposeUiTest {
        assertHoverCoversTheClickableArea("toggle", { onNodeWithTag("toggle").performMouseInput { moveTo(center) } }) {
            TembraToggle(checked = false, onCheckedChange = {}, testTag = "toggle")
        }
    }

    @Test
    fun anIconButton() = runComposeUiTest {
        val source = MutableInteractionSource()
        assertHoverCoversTheClickableArea("button", hoverWith(source)) {
            TransportButton(icon = TembraIcon.Undo, description = "Undo", onClick = {}, interactionSource = source, testTag = "button")
        }
    }
}
