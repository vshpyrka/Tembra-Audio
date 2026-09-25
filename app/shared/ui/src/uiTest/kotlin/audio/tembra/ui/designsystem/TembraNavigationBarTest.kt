package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
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
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bottom bar component (spec FR-003, FR-008, FR-015, FR-019 and the narrow-window edge cases). */
@OptIn(ExperimentalTestApi::class)
class TembraNavigationBarTest {

    private var inputModeManager: InputModeManager? = null
    private var primary: Color = Color.Unspecified
    private var onSurfaceVariant: Color = Color.Unspecified
    private var onSurface: Color = Color.Unspecified

    private fun ComposeUiTest.setBar(width: Dp? = null, fontScale: Float? = null, selectedId: String = "home", onSelect: (String) -> Unit = {}) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                primary = TembraTheme.colors.primary
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                onSurface = TembraTheme.colors.onSurface
                val bar: @androidx.compose.runtime.Composable () -> Unit = {
                    if (width == null) TembraNavigationBar(sixNavigationItems, selectedId, onSelect)
                    else Box(Modifier.width(width)) { TembraNavigationBar(sixNavigationItems, selectedId, onSelect) }
                }
                if (fontScale == null) bar() else CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { bar() }
            }
        }
    }

    private fun ComposeUiTest.assertSixItemsFitWithin(containerTag: String) {
        val container = onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        for (item in sixNavigationItems) {
            val tag = "nav.item.${item.id}"
            val itemBounds = onNodeWithTag(tag).assertDisplayedWithin(container, tag).getUnclippedBoundsInRoot()
            onNode(hasText(item.label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .assertDisplayedWithin(itemBounds, "label ${item.label}")
        }
    }

    @Test
    fun rendersSixLabelledItemsAndMarksTheSelectedOne() = runComposeUiTest {
        setBar()
        onNodeWithTag("chrome.bottomBar").assertExists()
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
        setBar(onSelect = { selected = it })
        onNodeWithTag("nav.item.tuner").performClick()
        assertEquals("tuner", selected)
    }

    @Test
    fun fitsSixLabelledItemsAt320dp() = runComposeUiTest {
        setBar(width = 320.dp)
        assertSixItemsFitWithin("chrome.bottomBar")
    }

    @Test
    fun fitsSixLabelledItemsAt320dpWithLargeFonts() = runComposeUiTest {
        setBar(width = 320.dp, fontScale = 2f)
        assertSixItemsFitWithin("chrome.bottomBar")
    }

    @Test
    fun everyLabelStaysOnOneLineAt360dp() = runComposeUiTest {
        setBar(width = 360.dp)
        for (item in sixNavigationItems) {
            val layout = onNode(hasText(item.label) and hasAnyAncestor(hasTestTag("nav.item.${item.id}")), useUnmergedTree = true).textLayoutResult()
            assertEquals(1, layout.lineCount, "${item.label} wraps at 360 dp")
        }
    }

    @Test
    fun everyItemIsAnAccessibleTab() = runComposeUiTest {
        setBar()
        for (item in sixNavigationItems) {
            onNodeWithTag("nav.item.${item.id}").assertIsAccessibleNavigationItem(item.label)
        }
    }

    /** Compose keeps clickables unfocusable while a device is in touch mode; a keyboard or mouse switches it. */
    private fun ComposeUiTest.enterKeyboardMode(): Boolean {
        val keyboard = runOnUiThread { inputModeManager!!.requestInputMode(InputMode.Keyboard) }
        if (!keyboard) println("platform stays in touch mode, keyboard path covered on iOS")
        return keyboard
    }

    @Test
    fun keyboardActivatesAnItem() = runComposeUiTest {
        var selected: String? = null
        setBar(onSelect = { selected = it })
        if (!enterKeyboardMode()) return@runComposeUiTest
        onNodeWithTag("nav.item.metronome").requestFocus()
        onNodeWithTag("nav.item.metronome").assertIsFocused()
        onNodeWithTag("nav.item.metronome").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("metronome", selected)
    }

    @Test
    fun focusedItemShowsAnIndicator() = runComposeUiTest {
        setBar()
        if (!enterKeyboardMode()) return@runComposeUiTest
        onNodeWithTag("nav.item.metronome").requestFocus()
        onNodeWithTag("nav.item.metronome").assertIsFocused()
        waitForIdle()
        val image = onNodeWithTag("nav.item.metronome").captureToImage()
        assertTrue(image.containsColor(primary), "no primary-coloured focus ring on the focused item")
    }

    @Test
    fun hoverRaisesTheLabelColour() = runComposeUiTest {
        setBar()
        val before = onNodeWithTag("nav.item.tuner").captureToImage()
        assertTrue(before.containsColor(onSurfaceVariant), "inactive label is not onSurfaceVariant before hover")
        onNodeWithTag("nav.item.tuner").performMouseInput { enter(center); moveTo(center) }
        waitForIdle()
        val after = onNodeWithTag("nav.item.tuner").captureToImage()
        assertTrue(after.containsColor(onSurface), "hovered label is not onSurface")
    }
}
