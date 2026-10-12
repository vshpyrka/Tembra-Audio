package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
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
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The design's segmented selector in both sizes (contracts/design-system-additions.md, spec FR-002, FR-024, FR-025). */
@OptIn(ExperimentalTestApi::class)
class TembraSegmentedControlTest {

    private val instruments = listOf(SegmentSpec("guitar", "Guitar"), SegmentSpec("cello", "Cello"))
    private val stringCounts = listOf(SegmentSpec("6", "6-String"), SegmentSpec("7", "7-String"))

    private var inputModeManager: InputModeManager? = null
    private var onSurface: Color = Color.Unspecified
    private var onSurfaceVariant: Color = Color.Unspecified
    private var primary: Color = Color.Unspecified

    private fun ComposeUiTest.setControl(
        items: List<SegmentSpec> = instruments,
        selectedId: String = items.first().id,
        style: SegmentedStyle = SegmentedStyle.Large,
        accent: SegmentAccent = SegmentAccent.Primary,
        widths: SegmentWidths = SegmentWidths.Content,
        width: Dp? = null,
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                onSurface = TembraTheme.colors.onSurface
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                primary = TembraTheme.colors.primary
                TembraSegmentedControl(
                    items = items,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    style = style,
                    modifier = if (width != null) Modifier.width(width) else Modifier,
                    accent = accent,
                    widths = widths,
                    testTag = "seg",
                )
            }
        }
    }

    @Test
    fun equalWidthsShareTheControl() = runComposeUiTest {
        setControl(items = stringCounts, style = SegmentedStyle.Small, widths = SegmentWidths.Equal, width = 300.dp)
        val six = onNodeWithTag("seg.6.surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val seven = onNodeWithTag("seg.7.surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((six.width - seven.width).value) < 1f, "segments differ: ${six.width} vs ${seven.width}")
        // The container's 2 dp padding on each side and the 2 dp gap are the only width the segments do not share.
        assertTrue(six.width + seven.width >= 300.dp - 8.dp, "segments do not fill the control: ${six.width} + ${seven.width}")
        onNodeWithTag("seg.6").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun contentWidthsHugTheLabels() = runComposeUiTest {
        setControl(items = stringCounts, style = SegmentedStyle.Small, width = 300.dp)
        val six = onNodeWithTag("seg.6.surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val seven = onNodeWithTag("seg.7.surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(six.width + seven.width < 240.dp, "segments were stretched: ${six.width} + ${seven.width}")
    }

    /** The instruments with the [id] segment driven by [source], so hover and focus are emitted rather than simulated. */
    private fun instrumentsWith(id: String, source: MutableInteractionSource): List<SegmentSpec> =
        instruments.map { if (it.id == id) it.copy(interactionSource = source) else it }

    @Test
    fun rendersLabelledTabsAndMarksTheSelection() = runComposeUiTest {
        setControl()
        onNodeWithTag("seg.guitar").assert(hasText("Guitar")).assertIsSelected()
        onNodeWithTag("seg.cello").assert(hasText("Cello")).assertIsNotSelected()
        for (id in listOf("guitar", "cello")) {
            onNodeWithTag("seg.$id").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
    }

    @Test
    fun clickSelectsAnotherSegmentAndReselectingIsANoOp() = runComposeUiTest {
        var selected: String? = null
        setControl(onSelect = { selected = it })
        onNodeWithTag("seg.guitar").performClick()
        assertNull(selected)
        onNodeWithTag("seg.cello").performClick()
        assertEquals("cello", selected)
    }

    @Test
    fun everySegmentMeetsTheTouchTarget() = runComposeUiTest {
        setControl()
        for (id in listOf("guitar", "cello")) {
            onNodeWithTag("seg.$id").assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
        }
    }

    @Test
    fun hoverRaisesTheLabelColour() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setControl(items = instrumentsWith("cello", hover))
        val label = onNode(hasText("Cello") and hasAnyAncestor(hasTestTag("seg.cello")), useUnmergedTree = true)
        val before = label.captureToImage().averageColor()
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        val after = label.captureToImage().averageColor()
        assertTrue(after.luminance() > before.luminance(), "hover did not brighten the label: $before -> $after")
        assertTrue(onNodeWithTag("seg.cello").captureToImage().containsColor(onSurface), "hovered label is not onSurface")
    }

    @Test
    fun focusShowsARing() = runComposeUiTest {
        val focus = MutableInteractionSource()
        setControl(items = instrumentsWith("cello", focus))
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        waitForIdle()
        assertTrue(onNodeWithTag("seg.cello").captureToImage().containsColor(primary), "no primary focus ring")
    }

    @Test
    fun keyboardEnterSelectsTheFocusedSegment() = runComposeUiTest {
        var selected: String? = null
        setControl(onSelect = { selected = it })
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("seg.cello").requestFocus()
        onNodeWithTag("seg.cello").assertIsFocused()
        onNodeWithTag("seg.cello").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("cello", selected)
    }

    @Test
    fun smallSubtleStyleDraws28dpSegmentsInside44dpTargets() = runComposeUiTest {
        var selected: String? = null
        setControl(items = stringCounts, selectedId = "6", style = SegmentedStyle.Small, accent = SegmentAccent.Subtle, onSelect = { selected = it })
        onNodeWithTag("seg.6").assert(hasText("6-String")).assertIsSelected()
        onNodeWithTag("seg.7").assert(hasText("7-String")).assertIsNotSelected()
        onNodeWithTag("seg.6", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithTag("seg.6.surface", useUnmergedTree = true).assertHeightIsEqualTo(28.dp)
        onNodeWithTag("seg.6").assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
        onNodeWithTag("seg.7").assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
        onNodeWithTag("seg.7").performClick()
        assertEquals("7", selected)
    }

    @Test
    fun largeStyleDraws32dpSegments() = runComposeUiTest {
        setControl()
        onNodeWithTag("seg.guitar.surface", useUnmergedTree = true).assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun selectionFollowsTheState() = runComposeUiTest {
        var selectedId by mutableStateOf("guitar")
        setContent {
            TembraTheme {
                TembraSegmentedControl(items = instruments, selectedId = selectedId, onSelect = { selectedId = it }, style = SegmentedStyle.Large, testTag = "seg")
            }
        }
        onNodeWithTag("seg.cello").performClick()
        waitForIdle()
        onNodeWithTag("seg.cello").assertIsSelected()
        onNodeWithTag("seg.guitar").assertIsNotSelected()
    }
}
