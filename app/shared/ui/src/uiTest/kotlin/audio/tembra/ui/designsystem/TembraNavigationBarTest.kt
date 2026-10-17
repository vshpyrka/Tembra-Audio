package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bottom bar component (spec FR-003, FR-008, FR-014, FR-015, FR-019 and the narrow-window edge cases): Material 3
 * Expressive's short navigation bar with icon-only items since 2026-10-17, its height Material's own.
 */
@OptIn(ExperimentalTestApi::class)
class TembraNavigationBarTest {

    private var inputModeManager: InputModeManager? = null
    private var focusRing: Color = Color.Unspecified

    private fun ComposeUiTest.setBar(
        items: List<NavigationItemSpec> = fiveNavigationItems,
        width: Dp? = null,
        fontScale: Float? = null,
        selectedId: String = "timeline",
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                // Material's inset focus ring strokes its outer edge in the scheme's secondary colour.
                focusRing = MaterialTheme.colorScheme.secondary
                val bar: @androidx.compose.runtime.Composable () -> Unit = {
                    if (width == null) TembraNavigationBar(items, selectedId, onSelect)
                    else Box(Modifier.width(width)) { TembraNavigationBar(items, selectedId, onSelect) }
                }
                if (fontScale == null) bar() else CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { bar() }
            }
        }
    }

    /** The five items with [id] driven by [source], so hover and focus are emitted rather than simulated. */
    private fun itemsWith(id: String, source: MutableInteractionSource): List<NavigationItemSpec> =
        fiveNavigationItems.map { if (it.id == id) it.copy(interactionSource = source) else it }

    private fun ComposeUiTest.assertFiveItemsFitWithin(containerTag: String) {
        val container = onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        for (item in fiveNavigationItems) {
            val tag = "nav.item.${item.id}"
            onNodeWithTag(tag).assertDisplayedWithin(container, tag)
        }
    }

    @Test
    fun rendersFiveIconOnlyItemsAndMarksTheSelectedOne() = runComposeUiTest {
        setBar()
        onNodeWithTag("chrome.bottomBar").assertExists()
        for (item in fiveNavigationItems) {
            onNodeWithTag("nav.item.${item.id}").assertExists()
            onAllNodes(hasText(item.label) and hasAnyAncestor(hasTestTag("nav.item.${item.id}")), useUnmergedTree = true)
                .assertCountEquals(0)
        }
        onNodeWithTag("nav.item.timeline").assertIsSelected()
        for (item in fiveNavigationItems.drop(1)) {
            onNodeWithTag("nav.item.${item.id}").assertIsNotSelected()
        }
    }

    @Test
    fun clickCallsOnSelectWithTheItemId() = runComposeUiTest {
        var selected: String? = null
        setBar(onSelect = { selected = it })
        onNodeWithTag("nav.item.tuner").performClick()
        assertEquals("tuner", selected)
    }

    @Test
    fun fitsFiveItemsAt320dp() = runComposeUiTest {
        setBar(width = 320.dp)
        assertFiveItemsFitWithin("chrome.bottomBar")
    }

    @Test
    fun fitsFiveItemsAt320dpWithLargeFonts() = runComposeUiTest {
        setBar(width = 320.dp, fontScale = 2f)
        assertFiveItemsFitWithin("chrome.bottomBar")
    }

    /** With no labels the bar has nothing to grow for: the largest font leaves its height as Material sets it. */
    @Test
    fun largeFontsLeaveTheBarHeightUnchanged() = runComposeUiTest {
        var fontScale by mutableStateOf(1f)
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) {
                    TembraNavigationBar(fiveNavigationItems, "timeline", onSelect = {})
                }
            }
        }
        val regular = onNodeWithTag("chrome.bottomBar").getUnclippedBoundsInRoot().height
        fontScale = 2f
        waitForIdle()
        assertEquals(regular, onNodeWithTag("chrome.bottomBar").getUnclippedBoundsInRoot().height)
    }

    @Test
    fun everyItemIsAnAccessibleTab() = runComposeUiTest {
        setBar()
        for (item in fiveNavigationItems) {
            onNodeWithTag("nav.item.${item.id}").assertIsAccessibleNavigationItem(item.label)
        }
    }

    @Test
    fun keyboardActivatesAnItem() = runComposeUiTest {
        var selected: String? = null
        setBar(onSelect = { selected = it })
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("nav.item.metronome").requestFocus()
        onNodeWithTag("nav.item.metronome").assertIsFocused()
        onNodeWithTag("nav.item.metronome").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("metronome", selected)
    }

    @Test
    fun focusedItemShowsAnIndicator() = runComposeUiTest {
        val focus = MutableInteractionSource()
        setBar(items = itemsWith("metronome", focus))
        assertFalse(onNodeWithTag("nav.item.metronome").captureToImage().containsColor(focusRing), "focus ring before focus")
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        waitForIdle()
        val image = onNodeWithTag("nav.item.metronome").captureToImage()
        assertTrue(image.containsColor(focusRing), "no focus ring on the focused item")
    }

    @Test
    fun hoverIsVisible() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setBar(items = itemsWith("tuner", hover))
        val before = onNodeWithTag("nav.item.tuner").captureToImage()
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        val after = onNodeWithTag("nav.item.tuner").captureToImage()
        assertFalse(after.looksLike(before), "hovering the item changed nothing on screen")
    }

    /** Icon-only controls show their name on hover (Constitution IV): the item's label as a tooltip. */
    @Test
    fun hoverShowsTheLabelAsATooltip() = runComposeUiTest {
        setBar()
        onNode(hasText("Tuner")).assertDoesNotExist()
        onNodeWithTag("nav.item.tuner").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("Tuner")).assertExists()
    }
}
