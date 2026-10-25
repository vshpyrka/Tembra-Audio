package audio.tembra.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import audio.tembra.ui.navigation.TestViewModelFactory
import audio.tembra.ui.session.FakePlaybackGuard
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import audio.tembra.ui.theme.TembraTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

/** The Android system back action returns to Timeline, the start destination, then leaves the app (spec FR-010). */
class AndroidBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backReturnsToTimelineThenFinishesTheActivity() {
        rule.setContent {
            // The real destinations compose their ViewModels from the graph; the shell test factory stands in for it.
            val factory = remember { TestViewModelFactory() }
            CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
                TembraTheme { NavigationShell(viewModel = ShellViewModel(FakePlaybackGuard()), layoutMode = LayoutMode.BottomBar) }
            }
        }
        rule.onNodeWithTag("nav.item.projects").performClick()
        rule.onNodeWithTag("screen.projects").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("screen.timeline").assertIsDisplayed()
        assertFailsWith<NoActivityResumedException> { Espresso.pressBack() }
    }
}
