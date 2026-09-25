package audio.tembra.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import audio.tembra.ui.theme.TembraTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

/** The Android system back action returns to Home, then leaves the app (spec FR-010). */
class AndroidBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backReturnsToHomeThenFinishesTheActivity() {
        rule.setContent { TembraTheme { NavigationShell(viewModel = ShellViewModel(), layoutMode = LayoutMode.BottomBar) } }
        rule.onNodeWithTag("nav.item.tuner").performClick()
        rule.onNodeWithTag("screen.tuner").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("screen.home").assertIsDisplayed()
        assertFailsWith<NoActivityResumedException> { Espresso.pressBack() }
    }
}
