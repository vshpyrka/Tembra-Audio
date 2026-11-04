package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dropdown of contracts/design-system-additions.md: rows, enablement, selection, hover and focus through injected sources. */
@OptIn(ExperimentalTestApi::class)
class TembraMenuTest {

    private var surfaceContainerHigh: Color = Color.Unspecified
    private var primary: Color = Color.Unspecified

    private fun ComposeUiTest.setMenu(items: List<MenuItemSpec>, onSelect: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        setContent {
            TembraTheme {
                surfaceContainerHigh = TembraTheme.colors.surfaceContainerHigh
                primary = TembraTheme.colors.primary
                TembraMenu(expanded = true, onDismiss = onDismiss, items = items, onSelect = onSelect, testTag = "menu") {
                    Text("Anchor")
                }
            }
        }
        mainClock.autoAdvance = false
    }

    private val items = listOf(
        MenuItemSpec("settings", "Track settings"),
        MenuItemSpec("up", "Move up", enabled = false),
        MenuItemSpec("delete", "Delete track"),
    )

    @Test
    fun listsEveryRowAs44dpWithItsLabelAndEnablement() = runComposeUiTest {
        setMenu(items)
        onNodeWithText("Anchor").assertExists()
        onNodeWithTag("menu.settings").assertIsEnabled().assertHeightIsAtLeast(44.dp)
        onNodeWithTag("menu.up").assertIsNotEnabled()
        onNodeWithTag("menu.delete").assertIsEnabled()
        onNodeWithText("Move up").assertExists()
    }

    @Test
    fun choosingARowReportsItsIdAndDismisses() = runComposeUiTest {
        var selected: String? = null
        var dismissed = 0
        setMenu(items, onSelect = { selected = it }, onDismiss = { dismissed++ })
        mainClock.autoAdvance = true
        onNodeWithTag("menu.delete").performClick()
        assertEquals("delete", selected)
        assertEquals(1, dismissed)
    }

    @Test
    fun aDisabledRowSelectsNothing() = runComposeUiTest {
        var selected: String? = null
        setMenu(items, onSelect = { selected = it })
        mainClock.autoAdvance = true
        onNodeWithTag("menu.up").performClick()
        assertEquals(null, selected)
    }

    /** A toggle row (the timeline's "Overview strip") reads selected or not and is checked only while on; an action row reads neither. */
    @Test
    fun aChoiceRowReadsItsSelectionAndChecksTheCurrentOne() = runComposeUiTest {
        setMenu(
            listOf(
                MenuItemSpec("on", "Overview strip", selected = true),
                MenuItemSpec("off", "Snap to grid", selected = false),
                MenuItemSpec("action", "Metronome settings"),
            ),
        )
        onNodeWithTag("menu.on").assertIsSelected()
        onNodeWithTag("menu.on.check", useUnmergedTree = true).assertExists()
        onNodeWithTag("menu.off").assertIsNotSelected()
        onNodeWithTag("menu.off.check", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("menu.action").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        onNodeWithTag("menu.action.check", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun hoverTintsTheRowAndFocusDrawsTheRing() = runComposeUiTest {
        val hover = MutableInteractionSource()
        val focus = MutableInteractionSource()
        setMenu(listOf(MenuItemSpec("a", "Hovered", interactionSource = hover), MenuItemSpec("b", "Focused", interactionSource = focus)))
        // The fill where the row draws no text: the label brightens on hover too, which moved a whole-row average to
        // halfway on the Redmi's 3x screen (2026-11-04).
        val resting = onNodeWithTag("menu.a").captureToImage().fillColor()
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        mainClock.advanceTimeBy(FRAMES_MS)
        val hovered = onNodeWithTag("menu.a").captureToImage().fillColor()
        assertTrue(hovered.isCloserTo(surfaceContainerHigh, resting), "the hovered row $hovered did not move toward surfaceContainerHigh $surfaceContainerHigh from $resting")
        assertTrue(focus.tryEmit(FocusInteraction.Focus()))
        mainClock.advanceTimeBy(FRAMES_MS)
        assertTrue(onNodeWithTag("menu.b").captureToImage().containsColor(primary), "the focused row draws no primary ring")
    }

    /** The row's fill near its trailing edge, clear of the label and of a check mark (an unselected row has none). */
    private fun ImageBitmap.fillColor(): Color = averageColorAt(width - 8, height / 2, radius = 2)

    private companion object {
        const val FRAMES_MS = 100L
    }
}
