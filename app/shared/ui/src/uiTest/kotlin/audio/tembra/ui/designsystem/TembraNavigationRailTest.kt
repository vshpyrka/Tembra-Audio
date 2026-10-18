package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rail component (spec FR-003, FR-005, FR-014, FR-015, FR-019): Material 3 Expressive's collapsed wide navigation
 * rail with icon-only items since 2026-10-17, its width Material's own.
 */
@OptIn(ExperimentalTestApi::class)
class TembraNavigationRailTest {

    private var inputModeManager: InputModeManager? = null
    private var focusRing: Color = Color.Unspecified

    private fun ComposeUiTest.setRail(
        items: List<NavigationItemSpec> = fiveNavigationItems,
        fontScale: Float? = null,
        selectedId: String = "timeline",
        onSelect: (String) -> Unit = {},
    ) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                // Material's inset focus ring strokes its outer edge in the scheme's secondary colour.
                focusRing = MaterialTheme.colorScheme.secondary
                if (fontScale == null) {
                    TembraNavigationRail(items, selectedId, onSelect)
                } else {
                    CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { TembraNavigationRail(items, selectedId, onSelect) }
                }
            }
        }
    }

    /** The five items with [id] driven by [source], so hover and focus are emitted rather than simulated. */
    private fun itemsWith(id: String, source: MutableInteractionSource): List<NavigationItemSpec> =
        fiveNavigationItems.map { if (it.id == id) it.copy(interactionSource = source) else it }

    private fun ComposeUiTest.assertFiveItemsFitWithinTheRail() {
        val rail = onNodeWithTag("chrome.rail").getUnclippedBoundsInRoot()
        for (item in fiveNavigationItems) {
            val tag = "nav.item.${item.id}"
            onNodeWithTag(tag).assertDisplayedWithin(rail, tag)
        }
    }

    @Test
    fun rendersFiveIconOnlyItemsAndMarksTheSelectedOne() = runComposeUiTest {
        setRail()
        onNodeWithTag("chrome.rail").assertExists()
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
        setRail(onSelect = { selected = it })
        onNodeWithTag("nav.item.tuner").performClick()
        assertEquals("tuner", selected)
    }

    @Test
    fun fitsFiveItems() = runComposeUiTest {
        setRail()
        assertFiveItemsFitWithinTheRail()
    }

    @Test
    fun fitsFiveItemsWithLargeFonts() = runComposeUiTest {
        setRail(fontScale = 2f)
        assertFiveItemsFitWithinTheRail()
    }

    /** The items sit in the middle of the rail's width, not at its start edge (an iPad capture showed them flush left). */
    @Test
    fun itemsAreCentredInTheRail() = runComposeUiTest {
        setRail()
        val rail = onNodeWithTag("chrome.rail").getUnclippedBoundsInRoot()
        val railCentre = (rail.left + rail.right) / 2
        for (item in fiveNavigationItems) {
            val bounds = onNodeWithTag("nav.item.${item.id}").getUnclippedBoundsInRoot()
            val itemCentre = (bounds.left + bounds.right) / 2
            assertTrue(abs((itemCentre - railCentre).value) < 0.5f, "${item.id} is centred at $itemCentre, the rail at $railCentre")
        }
    }

    /** A phone held sideways leaves the rail less height than its items need: they scroll, and the last stays reachable. */
    @Test
    fun aShortRailScrollsToItsLastItem() = runComposeUiTest {
        var selected: String? = null
        setContent {
            TembraTheme {
                Box(Modifier.height(240.dp)) { TembraNavigationRail(fiveNavigationItems, "timeline", onSelect = { selected = it }) }
            }
        }
        onNodeWithTag("nav.item.profile").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("profile", selected)
    }

    @Test
    fun everyItemIsAnAccessibleTab() = runComposeUiTest {
        setRail()
        for (item in fiveNavigationItems) {
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
        assertFalse(onNodeWithTag("nav.item.metronome").captureToImage().containsColor(focusRing), "focus ring before focus")
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        waitForIdle()
        val image = onNodeWithTag("nav.item.metronome").captureToImage()
        assertTrue(image.containsColor(focusRing), "no focus ring on the focused item")
    }

    @Test
    fun hoverIsVisible() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setRail(items = itemsWith("tuner", hover))
        val before = onNodeWithTag("nav.item.tuner").captureToImage()
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        val after = onNodeWithTag("nav.item.tuner").captureToImage()
        assertFalse(after.looksLike(before), "hovering the item changed nothing on screen")
    }

    /** Icon-only controls show their name on hover (Constitution IV): the item's label as a tooltip. */
    @Test
    fun hoverShowsTheLabelAsATooltip() = runComposeUiTest {
        setRail()
        onNode(hasText("Tuner")).assertDoesNotExist()
        onNodeWithTag("nav.item.tuner").performMouseInput { moveTo(center) }
        waitForIdle()
        onNode(hasText("Tuner")).assertExists()
    }
}
