package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Drives the stateless shell through every layout (spec FR-004 to FR-009, FR-020). The destination switched to is
 * Metronome, a placeholder: the Tuner screen obtains a ViewModel from the Metro graph, which these tests do not provide.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationShellTest {

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    /** Frames the NavDisplay needs to start a transition or dispose the outgoing entry once it has ended. */
    private val SETTLE_FRAMES = 3

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private var inputModeManager: InputModeManager? = null

    /** TembraNavDisplay installs the ViewModel-store entry decorator, which needs a store owner above it. */
    private fun ComposeUiTest.withShellLocals(content: @Composable () -> Unit) {
        setContent {
            inputModeManager = LocalInputModeManager.current
            val owner = remember { TestViewModelStoreOwner() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                TembraTheme(content)
            }
        }
    }

    @Test
    fun bottomBarLayout() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        onNodeWithTag("chrome.bottomBar").assertExists()
        onNodeWithTag("chrome.rail").assertDoesNotExist()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        for (destination in Destination.entries) {
            onNodeWithTag("nav.item.${destination.id}").assertExists()
        }
        onNodeWithTag("screen.home").assertIsDisplayed()
        onNodeWithTag("chrome.topBar.title").assertTextEquals("Home")
    }

    @Test
    fun railLayout() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.Rail, onSelect = {}, onBack = {}) }
        onNodeWithTag("chrome.rail").assertExists()
        onNodeWithTag("chrome.bottomBar").assertDoesNotExist()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        for (destination in Destination.entries) {
            onNodeWithTag("nav.item.${destination.id}").assertExists()
        }
        onNodeWithTag("screen.home").assertIsDisplayed()
    }

    @Test
    fun railWithPanelWithoutSupportingContentShowsNoPane() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(Destination.Metronome), LayoutMode.RailWithPanel, onSelect = {}, onBack = {}) }
        onNodeWithTag("chrome.rail").assertExists()
        onNodeWithTag("chrome.bottomBar").assertDoesNotExist()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        onNodeWithTag("screen.metronome").assertIsDisplayed()
    }

    @Test
    fun layoutSwitchKeepsTheSelection() = runComposeUiTest {
        var layoutMode by mutableStateOf(LayoutMode.BottomBar)
        withShellLocals { NavigationShellContent(ShellUiState(Destination.Metronome), layoutMode, onSelect = {}, onBack = {}) }
        for (mode in listOf(LayoutMode.Rail, LayoutMode.RailWithPanel, LayoutMode.BottomBar)) {
            layoutMode = mode
            waitForIdle()
            onNodeWithTag("nav.item.metronome").assertIsSelected()
            onNodeWithTag("screen.metronome").assertIsDisplayed()
            onNodeWithTag("chrome.topBar.title").assertTextEquals("Metronome")
        }
    }

    @Test
    fun railWithPanelShowsThePaneOnHomeOnly() = runComposeUiTest {
        var state by mutableStateOf(ShellUiState())
        var layoutMode by mutableStateOf(LayoutMode.RailWithPanel)
        withShellLocals { NavigationShellContent(state, layoutMode, onSelect = {}, onBack = {}) }
        onNodeWithTag("chrome.supportingPane").assertIsDisplayed()
        onNode(hasText("Library") and hasAnyAncestor(hasTestTag("chrome.supportingPane"))).assertIsDisplayed()
        onNode(hasText("Loops and sounds will appear here.") and hasAnyAncestor(hasTestTag("chrome.supportingPane"))).assertIsDisplayed()
        // On a phone-sized test window the forced panel leaves Home no width; its presence is what matters here.
        onNodeWithTag("screen.home").assertExists()
        state = ShellUiState(Destination.Metronome)
        waitForIdle()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        onNodeWithTag("screen.metronome").assertIsDisplayed()
        state = ShellUiState()
        layoutMode = LayoutMode.Rail
        waitForIdle()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        onNodeWithTag("screen.home").assertIsDisplayed()
    }

    @Test
    fun homeShowsOnlyTitleAndSentence() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        val textNodes = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
        onNode(hasText("Home") and hasAnyAncestor(hasTestTag("screen.home")), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("screen.home.message").assertTextEquals("This is your starting point in Tembra Audio.")
        onAllNodes(hasAnyAncestor(hasTestTag("screen.home")) and textNodes, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun paneHasNoControls() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.RailWithPanel, onSelect = {}, onBack = {}) }
        onAllNodes(hasAnyAncestor(hasTestTag("chrome.supportingPane")) and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun selectingAnItemCallsOnSelectAndMarksIt() = runComposeUiTest {
        var state by mutableStateOf(ShellUiState())
        var selected: Destination? = null
        withShellLocals { NavigationShellContent(state, LayoutMode.BottomBar, onSelect = { selected = it }, onBack = {}) }
        onNodeWithTag("nav.item.metronome").performClick()
        assertEquals(Destination.Metronome, selected)
        state = ShellUiState(Destination.Metronome)
        waitForIdle()
        onNodeWithTag("nav.item.metronome").assertIsSelected()
        onNodeWithTag("nav.item.home").assertIsNotSelected()
        onNodeWithTag("chrome.topBar.title").assertTextEquals("Metronome")
        onNodeWithTag("screen.metronome").assertIsDisplayed()
    }

    @Test
    fun reselectingTheActiveItemIsANoOp() = runComposeUiTest {
        var selected: Destination? = null
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = { selected = it }, onBack = {}) }
        onNodeWithTag("nav.item.home").performClick()
        assertNull(selected)
    }

    @Test
    fun transitionFinishesWithinTheStandardDuration() = runComposeUiTest {
        var state by mutableStateOf(ShellUiState())
        withShellLocals { NavigationShellContent(state, LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        mainClock.autoAdvance = false
        state = ShellUiState(Destination.Metronome)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        // Mid-fade both destinations are on screen: no blank frame (spec SC-002).
        onNodeWithTag("screen.metronome").assertIsDisplayed()
        onNodeWithTag("screen.home").assertExists()
        mainClock.advanceTimeBy(300)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("screen.metronome").assertIsDisplayed()
        onNodeWithTag("screen.home").assertDoesNotExist()
    }

    @Test
    fun reduceMotionSwitchesInstantly() = runComposeUiTest(effectContext = zeroScale) {
        var state by mutableStateOf(ShellUiState())
        withShellLocals { NavigationShellContent(state, LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        mainClock.autoAdvance = false
        state = ShellUiState(Destination.Metronome)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        // A few frames later (well under the 300 ms fade) the switch is complete: no outgoing screen remains.
        onNodeWithTag("screen.metronome").assertIsDisplayed()
        onNodeWithTag("screen.home").assertDoesNotExist()
    }

    @Test
    fun keyboardActivatesAnItemThroughTheShell() = runComposeUiTest {
        var selected: Destination? = null
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = { selected = it }, onBack = {}) }
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("nav.item.projects").requestFocus()
        onNodeWithTag("nav.item.projects").assertIsFocused()
        onNodeWithTag("nav.item.projects").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(Destination.Projects, selected)
    }

    @Test
    fun everyItemMeetsTheTouchTargetInBothLayouts() = runComposeUiTest {
        var layoutMode by mutableStateOf(LayoutMode.BottomBar)
        withShellLocals { NavigationShellContent(ShellUiState(), layoutMode, onSelect = {}, onBack = {}) }
        for (mode in listOf(LayoutMode.BottomBar, LayoutMode.Rail)) {
            layoutMode = mode
            waitForIdle()
            for (destination in Destination.entries) {
                onNodeWithTag("nav.item.${destination.id}").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
            }
        }
    }
}
