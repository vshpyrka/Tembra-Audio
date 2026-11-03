package audio.tembra.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import audio.tembra.ui.drop.FakeProjectFromAudio
import audio.tembra.ui.drop.NoOpenedFiles
import audio.tembra.ui.navigation.TestViewModelFactory
import audio.tembra.ui.session.FakePlaybackGuard
import audio.tembra.ui.session.FakeSessionActivity
import audio.tembra.ui.theme.TembraTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlin.test.assertFailsWith
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** The Android system back action returns to Timeline, the start destination, then leaves the app (spec FR-010). */
class AndroidBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backReturnsToTimelineThenFinishesTheActivity() {
        // In a desktop window, back on the root activity does not finish it (Android's desktop windowing, feature 011).
        assumeFalse("a free-form window: run on a phone or a tablet in full screen", rule.activity.isInMultiWindowMode)
        rule.setContent {
            // The real destinations compose their ViewModels from the graph; the shell test factory stands in for it.
            val factory = remember { TestViewModelFactory() }
            CompositionLocalProvider(LocalMetroViewModelFactory provides factory) {
                TembraTheme { NavigationShell(viewModel = ShellViewModel(FakePlaybackGuard(), FakeSessionActivity(), NoOpenedFiles, FakeProjectFromAudio()), layoutMode = LayoutMode.BottomBar) }
            }
        }
        rule.onNodeWithTag("nav.item.projects").performClick()
        rule.onNodeWithTag("screen.projects").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("screen.timeline").assertIsDisplayed()
        assertFailsWith<NoActivityResumedException> { Espresso.pressBack() }
    }
}
