package audio.tembra.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the stateless shell through every layout (spec FR-004 to FR-009, FR-020). The destination switched to is
 * Projects, a placeholder: the Tuner and Metronome screens obtain a ViewModel from the Metro graph, which these tests do not provide.
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
    }

    /**
     * The frame has no top bar (2026-10-16): the selected item names the destination, so the content starts right under
     * the status bar (the safe-drawing top inset, zero where the window does not draw behind it) in every layout, and
     * the rail's surface reaches the top edge while its items stay clear of the inset.
     */
    @Test
    fun noLayoutHasATopBarAndTheContentStartsAtTheTopInset() = runComposeUiTest {
        var layoutMode by mutableStateOf(LayoutMode.BottomBar)
        var topInset = 0.dp
        var homeMargin = 0.dp
        withShellLocals {
            topInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
            homeMargin = TembraTheme.spacing.lg // the placeholder's own top margin, inside its tagged node
            NavigationShellContent(ShellUiState(), layoutMode, onSelect = {}, onBack = {})
        }
        for (mode in LayoutMode.entries) {
            layoutMode = mode
            waitForIdle()
            onNodeWithTag("chrome.topBar").assertDoesNotExist()
            val shell = onNodeWithTag("nav.shell").getUnclippedBoundsInRoot()
            val home = onNodeWithTag("screen.home").getUnclippedBoundsInRoot()
            val expected = topInset + homeMargin
            assertTrue(abs((home.top - shell.top - expected).value) < 0.5f, "$mode: Home starts ${home.top - shell.top} below the shell's top, not $expected (inset $topInset)")
            if (mode == LayoutMode.BottomBar) continue
            assertTrue(abs((onNodeWithTag("chrome.rail").getUnclippedBoundsInRoot().top - shell.top).value) < 0.5f, "$mode: the rail does not reach the top edge")
            val firstItem = onNodeWithTag("nav.item.${Destination.entries.first().id}").getUnclippedBoundsInRoot()
            assertTrue(firstItem.top >= shell.top + topInset - 0.5.dp, "$mode: the first rail item (${firstItem.top}) is under the top inset $topInset")
        }
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
        withShellLocals { NavigationShellContent(ShellUiState(Destination.Projects), LayoutMode.RailWithPanel, onSelect = {}, onBack = {}) }
        onNodeWithTag("chrome.rail").assertExists()
        onNodeWithTag("chrome.bottomBar").assertDoesNotExist()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        onNodeWithTag("screen.projects").assertIsDisplayed()
    }

    @Test
    fun layoutSwitchKeepsTheSelection() = runComposeUiTest {
        var layoutMode by mutableStateOf(LayoutMode.BottomBar)
        withShellLocals { NavigationShellContent(ShellUiState(Destination.Projects), layoutMode, onSelect = {}, onBack = {}) }
        for (mode in listOf(LayoutMode.Rail, LayoutMode.RailWithPanel, LayoutMode.BottomBar)) {
            layoutMode = mode
            waitForIdle()
            onNodeWithTag("nav.item.projects").assertIsSelected()
            onNodeWithTag("screen.projects").assertIsDisplayed()
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
        state = ShellUiState(Destination.Projects)
        waitForIdle()
        onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        onNodeWithTag("screen.projects").assertIsDisplayed()
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
        onNodeWithTag("nav.item.projects").performClick()
        assertEquals(Destination.Projects, selected)
        state = ShellUiState(Destination.Projects)
        waitForIdle()
        onNodeWithTag("nav.item.projects").assertIsSelected()
        onNodeWithTag("nav.item.home").assertIsNotSelected()
        onNodeWithTag("screen.projects").assertIsDisplayed()
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
        state = ShellUiState(Destination.Projects)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        // Mid-fade both destinations are on screen: no blank frame (spec SC-002).
        onNodeWithTag("screen.projects").assertIsDisplayed()
        onNodeWithTag("screen.home").assertExists()
        mainClock.advanceTimeBy(300)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("screen.projects").assertIsDisplayed()
        onNodeWithTag("screen.home").assertDoesNotExist()
    }

    @Test
    fun reduceMotionSwitchesInstantly() = runComposeUiTest(effectContext = zeroScale) {
        var state by mutableStateOf(ShellUiState())
        withShellLocals { NavigationShellContent(state, LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        mainClock.autoAdvance = false
        state = ShellUiState(Destination.Projects)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        // A few frames later (well under the 300 ms fade) the switch is complete: no outgoing screen remains.
        onNodeWithTag("screen.projects").assertIsDisplayed()
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
