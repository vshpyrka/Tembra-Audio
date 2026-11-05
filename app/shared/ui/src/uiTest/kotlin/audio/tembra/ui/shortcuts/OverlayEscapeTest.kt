package audio.tembra.ui.shortcuts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Escape's close-overlay action (feature 011, contracts/keyboard-shortcuts.md rule 4): the design system's overlays
 * register while open, so the action closes the newest first (a menu, then the shortcut sheet under it), then nothing.
 * On iPad, where UIKit keeps Escape from Compose, the menu bar's Escape command runs this action.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayEscapeTest {

    @Test
    fun closeOverlayClosesTheMenuThenTheSheetThenNothing() = runComposeUiTest {
        val stack = OverlayStack()
        var sheetOpen by mutableStateOf(true)
        var menuOpen by mutableStateOf(false)
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalOverlayStack provides stack) {
                    if (sheetOpen) ShortcutSheet(onDismiss = { sheetOpen = false })
                    Box(Modifier.size(200.dp)) {
                        TembraMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            items = listOf(MenuItemSpec("a", "A")),
                            onSelect = {},
                            testTag = "menu",
                        ) { Box(Modifier.size(10.dp)) }
                    }
                }
            }
        }
        menuOpen = true
        waitForIdle()
        onNodeWithTag("menu").assertExists()

        assertEquals(true, runOnIdle { stack.closeTop() })
        onNodeWithTag("menu").assertDoesNotExist()
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertExists()

        assertEquals(true, runOnIdle { stack.closeTop() })
        onNodeWithTag(SHORTCUT_SHEET_TAG).assertDoesNotExist()

        assertFalse(runOnIdle { stack.closeTop() })
    }
}
