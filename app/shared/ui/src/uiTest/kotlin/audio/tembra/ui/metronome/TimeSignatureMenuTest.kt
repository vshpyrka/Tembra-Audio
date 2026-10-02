package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.metronome.components.TimeSignatureMenu
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The compact layout's time-signature menu (contracts/design-system-additions.md, design iteration D1): a pill naming the
 * signature that opens the six signatures with a check on the current one; semantics, selection, 44 dp, hover and keys.
 */
@OptIn(ExperimentalTestApi::class)
class TimeSignatureMenuTest {

    private val chosen = mutableListOf<TimeSignature>()
    private var inputModeManager: InputModeManager? = null
    private val ids = TimeSignature.entries.map { it.id }

    private fun ComposeUiTest.setMenu(selected: TimeSignature = TimeSignature.FourFour) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                Box(Modifier.size(320.dp).background(TembraTheme.colors.surface), contentAlignment = Alignment.TopStart) {
                    TimeSignatureMenu(selected = selected, onSelect = { chosen += it })
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun theTriggerNamesTheSignature() = runComposeUiTest {
        setMenu(TimeSignature.SevenEight)
        onNodeWithTag("metronome.signature")
            .assertContentDescriptionEquals("Time signature, 7/8")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
        // The drawn label is there, out of the accessibility tree (the description names it).
        onNodeWithTag("metronome.signature.label", useUnmergedTree = true).assertExists()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertDoesNotExist()
    }

    @Test
    fun theMenuListsTheSixSignaturesWithTheCurrentOneChecked() = runComposeUiTest {
        setMenu()
        onNodeWithTag("metronome.signature").performClick()
        waitForIdle()
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertHeightIsAtLeast(44.dp)
        onNodeWithTag("metronome.signature.4-4").assertIsSelected()
        onNodeWithTag("metronome.signature.4-4.check", useUnmergedTree = true).assertExists()
        for (id in ids.drop(1)) {
            onNodeWithTag("metronome.signature.$id").assertIsNotSelected()
            onNodeWithTag("metronome.signature.$id.check", useUnmergedTree = true).assertDoesNotExist()
        }
        // The entries in the spec's order, top to bottom.
        val tops = ids.map { onNodeWithTag("metronome.signature.$it").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the entries are not in the spec's order")
    }

    /** The pill says whether its menu is open, as a dropdown trigger does. */
    @Test
    fun theTriggerReadsCollapsedOrExpanded() = runComposeUiTest {
        setMenu()
        onNodeWithTag("metronome.signature").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        onNodeWithTag("metronome.signature").performClick()
        waitForIdle()
        onNodeWithTag("metronome.signature").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNodeWithTag("metronome.signature.3-4").performClick()
        waitForIdle()
        onNodeWithTag("metronome.signature").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test
    fun anEntrySelectsItsSignatureAndClosesTheMenu() = runComposeUiTest {
        setMenu()
        onNodeWithTag("metronome.signature").performClick()
        waitForIdle()
        onNodeWithTag("metronome.signature.7-8").performClick()
        waitForIdle()
        assertEquals(listOf(TimeSignature.SevenEight), chosen)
        onNodeWithTag("metronome.signature.7-8").assertDoesNotExist()
        // The current entry selects nothing again.
        onNodeWithTag("metronome.signature").performClick()
        waitForIdle()
        onNodeWithTag("metronome.signature.4-4").performClick()
        waitForIdle()
        assertEquals(listOf(TimeSignature.SevenEight), chosen)
    }

    private fun changedPixels(before: ImageBitmap, after: ImageBitmap): Int {
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) for (x in 0 until minOf(a.width, b.width)) {
            if (a[x, y].distanceTo(b[x, y]) > 0.03f) count++
        }
        return count
    }

    @Test
    fun hoverChangesTheTrigger() = runComposeUiTest {
        setMenu()
        val rest = onNodeWithTag("metronome.signature").captureToImage()
        onNodeWithTag("metronome.signature").performMouseInput { moveTo(center) }
        waitForIdle()
        val changed = changedPixels(rest, onNodeWithTag("metronome.signature").captureToImage())
        assertTrue(changed > 20, "hover did not change the trigger ($changed pixels)")
    }

    /**
     * FR-029 (I4): the whole keyboard path. Focus the pill, Enter opens the menu, the arrow keys move along the entries
     * and Enter chooses one, which closes the menu. Runs on every lane that declares keyboard input mode (the iOS
     * simulator; the Android device lane declares none).
     */
    @Test
    fun theSignatureMenuOpensFromTheKeyboard() = runComposeUiTest {
        setMenu()
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.signature").requestFocus()
        waitForIdle()
        onNodeWithTag("metronome.signature").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertExists()
        // Down moves keyboard focus from the pill into the open menu and then along its entries (the pill keeps the
        // focus of the screen's own root, so the key goes to the focused entry once there is one).
        val entry = SemanticsMatcher("an entry of the menu") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("metronome.signature.") == true
        }
        val inMenu = isFocused() and entry
        fun focusedTarget() = if (onAllNodes(inMenu).fetchSemanticsNodes().isNotEmpty()) onNode(inMenu) else onNodeWithTag("metronome.signature")
        var presses = 0
        while (onAllNodes(inMenu and hasTestTag("metronome.signature.7-8")).fetchSemanticsNodes().isEmpty() && presses <= ids.size) {
            focusedTarget().performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            presses++
        }
        onNodeWithTag("metronome.signature.7-8").assertIsFocused()
        focusedTarget().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(TimeSignature.SevenEight), chosen)
        onNodeWithTag("metronome.signature.7-8").assertDoesNotExist()
    }

    @Test
    fun enterOpensTheMenuFromTheKeyboard() = runComposeUiTest {
        setMenu()
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.signature").requestFocus()
        waitForIdle()
        onNodeWithTag("metronome.signature").assertIsFocused()
        onNodeWithTag("metronome.signature").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("metronome.signature.3-4").assertExists()
    }
}
