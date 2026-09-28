package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertWidthIsEqualTo
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
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rail component (spec FR-003, FR-005, FR-015, FR-019). */
@OptIn(ExperimentalTestApi::class)
class TembraNavigationRailTest {

    private var inputModeManager: InputModeManager? = null
    private var primary: Color = Color.Unspecified
    private var surfaceContainerHigh: Color = Color.Unspecified
    private var onSurface: Color = Color.Unspecified

    private fun ComposeUiTest.setRail(
        items: List<NavigationItemSpec> = sixNavigationItems,
        fontScale: Float? = null,
        selectedId: String = "home",
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                primary = TembraTheme.colors.primary
                surfaceContainerHigh = TembraTheme.colors.surfaceContainerHigh
                onSurface = TembraTheme.colors.onSurface
                if (fontScale == null) {
                    TembraNavigationRail(items, selectedId, onSelect)
                } else {
                    CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { TembraNavigationRail(items, selectedId, onSelect) }
                }
            }
        }
    }

    /** The six items with [id] driven by [source], so hover and focus are emitted rather than simulated. */
    private fun itemsWith(id: String, source: MutableInteractionSource): List<NavigationItemSpec> =
        sixNavigationItems.map { if (it.id == id) it.copy(interactionSource = source) else it }

    private fun ComposeUiTest.assertSixItemsFitWithinTheRail() {
        val rail = onNodeWithTag("chrome.rail").getUnclippedBoundsInRoot()
        for (item in sixNavigationItems) {
            val tag = "nav.item.${item.id}"
            val itemBounds = onNodeWithTag(tag).assertDisplayedWithin(rail, tag).getUnclippedBoundsInRoot()
            onNode(hasText(item.label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .assertDisplayedWithin(itemBounds, "label ${item.label}")
        }
    }

    @Test
    fun rendersSixLabelledItemsAndMarksTheSelectedOne() = runComposeUiTest {
        setRail()
        onNodeWithTag("chrome.rail").assertExists().assertWidthIsEqualTo(64.dp)
        for (item in sixNavigationItems) {
            onNodeWithTag("nav.item.${item.id}").assertExists().assert(hasText(item.label))
        }
        onNodeWithTag("nav.item.home").assertIsSelected()
        for (item in sixNavigationItems.drop(1)) {
            onNodeWithTag("nav.item.${item.id}").assertIsNotSelected()
        }
    }

    @Test
    fun clickCallsOnSelectWithTheItemId() = runComposeUiTest {
        var selected: String? = null
        setRail(onSelect = { selected = it })
        onNodeWithTag("nav.item.tuner").performClick()
        assertEquals("tuner", selected)
    }

    @Test
    fun fitsSixLabelledItems() = runComposeUiTest {
        setRail()
        assertSixItemsFitWithinTheRail()
    }

    @Test
    fun fitsSixLabelledItemsWithLargeFonts() = runComposeUiTest {
        setRail(fontScale = 2f)
        assertSixItemsFitWithinTheRail()
    }

    @Test
    fun everyItemIsAnAccessibleTab() = runComposeUiTest {
        setRail()
        for (item in sixNavigationItems) {
            onNodeWithTag("nav.item.${item.id}").assertIsAccessibleNavigationItem(item.label)
        }
    }

    @Test
    fun keyboardActivatesAnItem() = runComposeUiTest {
        var selected: String? = null
        setRail(onSelect = { selected = it })
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
        setRail(items = itemsWith("metronome", focus))
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        waitForIdle()
        val image = onNodeWithTag("nav.item.metronome").captureToImage()
        assertTrue(image.containsColor(primary), "no primary-coloured focus ring on the focused item")
    }

    @Test
    fun hoverFillsTheItem() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setRail(items = itemsWith("tuner", hover))
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        val image = onNodeWithTag("nav.item.tuner").captureToImage()
        // Inside the rounded square, away from glyph and label: a few pixels in from the top-left corner.
        val inset = (image.width / 8).coerceAtLeast(2)
        val probe = image.colorAt(inset, inset)
        assertEquals(surfaceContainerHigh.red, probe.red, 0.02f, "hover fill red channel")
        assertEquals(surfaceContainerHigh.green, probe.green, 0.02f, "hover fill green channel")
        assertEquals(surfaceContainerHigh.blue, probe.blue, 0.02f, "hover fill blue channel")
        assertTrue(image.containsColor(onSurface), "hovered content is not onSurface")
    }
}
