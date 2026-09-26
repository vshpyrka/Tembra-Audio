package audio.tembra.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The design's segmented selector in both sizes (contracts/design-system-additions.md, spec FR-002, FR-024, FR-025). */
@OptIn(ExperimentalTestApi::class)
class TembraSegmentedControlTest {

    private val instruments = listOf(SegmentSpec("guitar", "Guitar", TembraIcon.Guitar), SegmentSpec("cello", "Cello", TembraIcon.Cello))
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
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                onSurface = TembraTheme.colors.onSurface
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                primary = TembraTheme.colors.primary
                TembraSegmentedControl(items = items, selectedId = selectedId, onSelect = onSelect, style = style, accent = accent, testTag = "seg")
            }
        }
    }

    private fun ComposeUiTest.enterKeyboardMode(): Boolean {
        val keyboard = runOnUiThread { inputModeManager!!.requestInputMode(InputMode.Keyboard) }
        if (!keyboard) println("platform stays in touch mode, keyboard path covered on iOS")
        return keyboard
    }

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
        setControl()
        val label = onNode(hasText("Cello") and hasAnyAncestor(hasTestTag("seg.cello")), useUnmergedTree = true)
        val before = label.captureToImage().averageColor()
        onNodeWithTag("seg.cello").performMouseInput { enter(center); moveTo(center) }
        waitForIdle()
        val after = label.captureToImage().averageColor()
        assertTrue(after.luminance() > before.luminance(), "hover did not brighten the label: $before -> $after")
        assertTrue(onNodeWithTag("seg.cello").captureToImage().containsColor(onSurface), "hovered label is not onSurface")
    }

    @Test
    fun keyboardFocusShowsARingAndEnterSelects() = runComposeUiTest {
        var selected: String? = null
        setControl(onSelect = { selected = it })
        if (!enterKeyboardMode()) return@runComposeUiTest
        onNodeWithTag("seg.cello").requestFocus()
        onNodeWithTag("seg.cello").assertIsFocused()
        waitForIdle()
        assertTrue(onNodeWithTag("seg.cello").captureToImage().containsColor(primary), "no primary focus ring")
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
