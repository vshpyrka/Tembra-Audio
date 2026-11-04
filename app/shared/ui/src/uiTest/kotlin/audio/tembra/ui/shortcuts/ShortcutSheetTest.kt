package audio.tembra.ui.shortcuts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.ScrollbarShown
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.profile.PROFILE_SHORTCUTS_TAG
import audio.tembra.ui.profile.ProfileScreen
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shortcut sheet (feature 011, spec FR-022; contracts/keyboard-shortcuts.md "The shortcut sheet"): every catalogue
 * entry, grouped by scope in navigation order (Everywhere first); opened from Profile's "Keyboard shortcuts" row; open
 * across a window change (its state is saveable in the shell); it takes focus and Escape closes it; a wheel scroll over
 * the list shows its scrollbar.
 */
@OptIn(ExperimentalTestApi::class)
class ShortcutSheetTest {

    @Test
    fun everyEntryIsListedInItsGroupInNavigationOrder() = runComposeUiTest {
        setContent { TembraTheme { ShortcutSheet(onDismiss = {}) } }
        // The list composes what it shows: scroll to each group's index in turn; it is that group, below the one before.
        ShortcutScope.entries.forEachIndexed { index, scope ->
            onNodeWithTag(SHORTCUT_LIST_TAG).performScrollToIndex(index)
            val top = onNodeWithTag(shortcutGroupTag(scope)).assertExists("group $index is not $scope").getUnclippedBoundsInRoot().top
            val previous = ShortcutScope.entries.getOrNull(index - 1)?.let(::shortcutGroupTag)
            if (previous != null && onAllNodesWithTag(previous).fetchSemanticsNodes().isNotEmpty()) {
                assertTrue(onNodeWithTag(previous).getUnclippedBoundsInRoot().top < top, "$scope is above the group before it")
            }
        }
        for (shortcut in ShortcutCatalogue.all) {
            onNodeWithTag(SHORTCUT_LIST_TAG).performScrollToNode(hasText(shortcut.label))
            onNodeWithText(shortcut.label).assertExists()
        }
    }

    @Test
    fun profilesRowOpensTheSheetAndItStaysOpenAcrossAWindowChange() = runComposeUiTest {
        var width by mutableStateOf(1200.dp)
        setContent {
            TembraTheme {
                var open by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.requiredSize(width, 800.dp)) {
                    ProfileScreen(onShowShortcuts = { open = true })
                    if (open) ShortcutSheet(onDismiss = { open = false })
                }
            }
        }
        onNodeWithText("Keyboard shortcuts").performClick()
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertExists()
        width = 500.dp
        waitForIdle()
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertExists()
    }

    @Test
    fun escapeClosesTheSheet() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent { TembraTheme { if (open) ShortcutSheet(onDismiss = { open = false }) } }
        // The sheet takes keyboard focus when it opens (contracts/keyboard-shortcuts.md rule 4).
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertIsFocused()
        onNodeWithTag(SHORTCUT_SHEET_TAG).performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun escapeReturnsFocusToTheRowThatOpenedIt() = runComposeUiTest {
        var manager: InputModeManager? = null
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                var open by remember { mutableStateOf(false) }
                Box(Modifier.requiredSize(1200.dp, 800.dp)) {
                    ProfileScreen(onShowShortcuts = { open = true })
                    if (open) ShortcutSheet(onDismiss = { open = false })
                }
            }
        }
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag(PROFILE_SHORTCUTS_TAG).requestFocus()
        onNodeWithTag(PROFILE_SHORTCUTS_TAG).performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertIsFocused()
        onNodeWithTag(SHORTCUT_SHEET_TAG).performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertDoesNotExist()
        onNodeWithTag(PROFILE_SHORTCUTS_TAG).assertIsFocused()
    }

    @Test
    fun aWheelScrollShowsTheSheetsScrollbar() = runComposeUiTest {
        setContent { TembraTheme { ShortcutSheet(onDismiss = {}) } }
        onNodeWithTag(SHORTCUT_LIST_TAG).performMouseInput { moveTo(center); scroll(2f) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(SHORTCUT_LIST_TAG).assert(SemanticsMatcher.expectValue(ScrollbarShown, true))
    }
}
