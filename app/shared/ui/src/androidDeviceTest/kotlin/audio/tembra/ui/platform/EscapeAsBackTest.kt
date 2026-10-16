package audio.tembra.ui.platform

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.navigation.LayoutMode
import audio.tembra.ui.navigation.NavigationShell
import audio.tembra.ui.navigation.ShellViewModel
import audio.tembra.ui.theme.TembraTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse

/**
 * A hardware keyboard's Escape goes back like the system back action, so a desktop window answers it (Pixel Tablet,
 * 2026-10-15: Compose consumed it and nothing happened), but from Home it never closes the app.
 */
class EscapeAsBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<EscapeAsBackActivity>()

    private fun pressEscape() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
        rule.waitForIdle()
    }

    @Test
    fun escapeReturnsHomeAndStaysThere() {
        rule.setContent { TembraTheme { NavigationShell(viewModel = ShellViewModel(), layoutMode = LayoutMode.Rail) } }
        rule.onNodeWithTag("nav.item.projects").performClick()
        rule.onNodeWithTag("screen.projects").assertIsDisplayed()
        pressEscape()
        rule.onNodeWithTag("screen.home").assertIsDisplayed()
        pressEscape()
        rule.onNodeWithTag("screen.home").assertIsDisplayed()
        assertFalse(rule.activity.isFinishing, "Escape on Home closed the app")
    }
}
