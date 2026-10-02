package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The equal-width chip grid of the metronome's time signature and subdivision rows (contracts/design-system-additions.md,
 * research R10). Keyboard traversal is not exercised here: the chips reuse `selectable`, whose key handling
 * `TembraSegmentedControlTest.keyboardEnterSelectsTheFocusedSegment` already covers on the lanes that declare keyboard mode.
 */
@OptIn(ExperimentalTestApi::class)
class TembraChipRowTest {

    private val ids = listOf("4-4", "3-4", "6-8", "2-4", "5-4", "7-8")
    private val signatures = ids.map { ChipSpec(it, it.replace('-', '/')) }

    private var onSurface: Color = Color.Unspecified
    private var onSurfaceVariant: Color = Color.Unspecified
    private var primary: Color = Color.Unspecified
    private var primaryContainer: Color = Color.Unspecified
    private var surfaceContainerHigh: Color = Color.Unspecified
    private var pixelDensity = 1f

    private fun ComposeUiTest.setChips(
        items: List<ChipSpec> = signatures,
        columns: Int = items.size,
        accent: ChipAccent = ChipAccent.Secondary,
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            TembraTheme {
                onSurface = TembraTheme.colors.onSurface
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                primary = TembraTheme.colors.primary
                primaryContainer = TembraTheme.colors.primaryContainer
                surfaceContainerHigh = TembraTheme.colors.surfaceContainerHigh
                pixelDensity = LocalDensity.current.density
                TembraChipRow(
                    items = items,
                    selectedId = "4-4",
                    onSelect = onSelect,
                    accent = accent,
                    modifier = Modifier.width(360.dp),
                    columns = columns,
                    testTag = "chips",
                )
            }
        }
    }

    private fun signaturesWith(id: String, source: MutableInteractionSource): List<ChipSpec> =
        signatures.map { if (it.id == id) it.copy(interactionSource = source) else it }

    @Test
    fun rendersLabelledTabsAndMarksTheSelection() = runComposeUiTest {
        setChips()
        for (spec in signatures) {
            val chip = onNodeWithTag("chips.${spec.id}").assert(hasText(spec.label))
            chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (spec.id == "4-4") chip.assertIsSelected() else chip.assertIsNotSelected()
        }
    }

    @Test
    fun clickSelectsAnotherChipAndReselectingIsANoOp() = runComposeUiTest {
        var selected: String? = null
        setChips(onSelect = { selected = it })
        onNodeWithTag("chips.4-4").performClick()
        assertNull(selected)
        onNodeWithTag("chips.6-8").performClick()
        assertEquals("6-8", selected)
    }

    @Test
    fun chipsDraw32dpInside44dpTargets() = runComposeUiTest {
        setChips()
        onNodeWithTag("chips.4-4.surface", useUnmergedTree = true).assertHeightIsEqualTo(32.dp)
        for (id in ids) {
            // The target is the project's touchTargetMin (44 dp), not Material's 48 dp default.
            onNodeWithTag("chips.$id").assertHeightIsEqualTo(44.dp).assertWidthIsAtLeast(44.dp)
        }
    }

    @Test
    fun thePrimaryAccentFillsTheSelectedChipWithPrimaryContainer() = runComposeUiTest {
        setChips(accent = ChipAccent.Primary)
        mainClock.autoAdvance = false
        val selected = onNodeWithTag("chips.4-4.surface", useUnmergedTree = true).captureToImage()
        val unselected = onNodeWithTag("chips.3-4.surface", useUnmergedTree = true).captureToImage()
        val inset = (FILL_PROBE_DP * pixelDensity).roundToInt()
        val selectedFill = selected.averageColorAt(inset, selected.height / 2)
        val unselectedFill = unselected.averageColorAt(inset, unselected.height / 2)
        assertTrue(selectedFill.approximately(primaryContainer), "selected fill $selectedFill is not primaryContainer $primaryContainer")
        assertTrue(unselectedFill.approximately(surfaceContainerHigh), "unselected fill $unselectedFill is not surfaceContainerHigh")
    }

    @Test
    fun chipsShareTheRowEqually() = runComposeUiTest {
        setChips()
        val widths = ids.map { onNodeWithTag("chips.$it.surface", useUnmergedTree = true).getUnclippedBoundsInRoot().let { b -> b.right - b.left } }
        for (width in widths) {
            assertTrue(kotlin.math.abs((width - widths.first()).value) < 1f, "chip widths differ: $widths")
        }
    }

    @Test
    fun threeColumnsWrapTheSixChipsIntoTwoRows() = runComposeUiTest {
        setChips(columns = 3)
        val first = onNodeWithTag("chips.4-4").getUnclippedBoundsInRoot()
        val fourth = onNodeWithTag("chips.2-4").getUnclippedBoundsInRoot()
        assertTrue(fourth.top >= first.bottom, "2/4 (top ${fourth.top}) is not below 4/4 (bottom ${first.bottom})")
        val third = onNodeWithTag("chips.6-8").getUnclippedBoundsInRoot()
        assertEquals(first.top, third.top, "4/4 and 6/8 are not on the same row")
        assertTrue(kotlin.math.abs((fourth.left - first.left).value) < 1f, "the second row does not start under the first")
    }

    @Test
    fun hoverRaisesAnUnselectedLabelToOnSurface() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setChips(items = signaturesWith("3-4", hover))
        mainClock.autoAdvance = false
        val label = onNode(hasText("3/4") and hasAnyAncestor(hasTestTag("chips.3-4")), useUnmergedTree = true)
        val before = label.captureToImage()
        val (x, y) = before.brightestPixel()
        val beforeBlock = before.averageColorAt(x, y)
        assertFalse(before.containsColor(onSurface), "the resting label already shows onSurface")
        assertTrue(before.containsColor(onSurfaceVariant), "the resting label is not onSurfaceVariant")
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        mainClock.advanceTimeBy(FRAMES_MS)
        val after = label.captureToImage()
        val afterBlock = after.averageColorAt(x, y)
        assertTrue(afterBlock.luminance() > beforeBlock.luminance(), "hover did not brighten the label: $beforeBlock -> $afterBlock")
        assertTrue(after.containsColor(onSurface), "the hovered label is not onSurface")
    }

    @Test
    fun focusDrawsThePrimaryRing() = runComposeUiTest {
        val focus = MutableInteractionSource()
        setChips(items = signaturesWith("3-4", focus))
        mainClock.autoAdvance = false
        assertFalse(onNodeWithTag("chips.3-4").captureToImage().containsColor(primary), "the ring is drawn before focus")
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        mainClock.advanceTimeBy(FRAMES_MS)
        assertTrue(onNodeWithTag("chips.3-4").captureToImage().containsColor(primary), "no primary focus ring")
    }

    @Test
    fun aLeadingGlyphIsDrawnInEveryChip() = runComposeUiTest {
        setChips(items = signatures.map { it.copy(icon = TembraIcon.MusicNote) })
        for (id in ids) {
            onNodeWithTag("chips.$id.icon", useUnmergedTree = true).assertExists()
        }
    }

    /** The pixel with the highest luminance: a point on the label's strokes, where a colour change is largest. */
    private fun ImageBitmap.brightestPixel(): Pair<Int, Int> {
        val pixels = toPixelMap()
        var best = 0 to 0
        var bestLuminance = -1f
        for (py in 0 until pixels.height) {
            for (px in 0 until pixels.width) {
                val l = pixels[px, py].luminance()
                if (l > bestLuminance) {
                    bestLuminance = l
                    best = px to py
                }
            }
        }
        return best
    }

    private companion object {
        /** Enough frames for the interaction collector to run and the chip to redraw with the clock frozen. */
        const val FRAMES_MS = 100L
        /** Fill probe 3 dp in from the start edge on the vertical centre: inside the 8 dp rounding, clear of the label. */
        const val FILL_PROBE_DP = 3
    }
}
