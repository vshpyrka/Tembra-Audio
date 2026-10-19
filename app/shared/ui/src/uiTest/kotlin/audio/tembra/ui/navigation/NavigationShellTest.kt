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
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
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
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the stateless shell through every layout (spec FR-004 to FR-009, FR-020). The shell starts on Timeline (Home was
 * removed on 2026-10-17) and the destination switched to is Projects, both placeholders: the Tuner and Metronome screens
 * obtain a ViewModel from the Metro graph, which these tests do not provide.
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
            val factory = remember { TestViewModelFactory() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalMetroViewModelFactory provides factory) {
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
        onNodeWithTag("screen.timeline").assertIsDisplayed()
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
        withShellLocals {
            topInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
            NavigationShellContent(ShellUiState(), layoutMode, onSelect = {}, onBack = {})
        }
        for (mode in LayoutMode.entries) {
            layoutMode = mode
            waitForIdle()
            onNodeWithTag("chrome.topBar").assertDoesNotExist()
            val shell = onNodeWithTag("nav.shell").getUnclippedBoundsInRoot()
            val start = onNodeWithTag("screen.timeline").getUnclippedBoundsInRoot()
            val expected = topInset // the Timeline's root fills its entry from the inset down (feature 009)
            assertTrue(abs((start.top - shell.top - expected).value) < 0.5f, "$mode: Timeline starts ${start.top - shell.top} below the shell's top, not $expected (inset $topInset)")
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
        onNodeWithTag("screen.timeline").assertIsDisplayed()
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

    /** Home and its Library panel were removed on 2026-10-17: no destination declares supporting content now. */
    @Test
    fun railWithPanelShowsNoPaneOnAnyDestination() = runComposeUiTest {
        var state by mutableStateOf(ShellUiState())
        withShellLocals { NavigationShellContent(state, LayoutMode.RailWithPanel, onSelect = {}, onBack = {}) }
        for (destination in listOf(Destination.Timeline, Destination.Projects, Destination.Profile)) {
            state = ShellUiState(destination)
            waitForIdle()
            onNodeWithTag("screen.${destination.id}").assertIsDisplayed()
            onNodeWithTag("chrome.supportingPane").assertDoesNotExist()
        }
    }

    /** The start destination is the arranger itself (feature 009): its toolbar and transport bar, no placeholder sentence. */
    @Test
    fun timelineShowsTheArrangerChrome() = runComposeUiTest {
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = {}, onBack = {}) }
        onNodeWithTag("screen.timeline").assertIsDisplayed()
        onNodeWithTag("timeline.toolbar").assertExists()
        onNodeWithTag("transport.bar").assertExists()
        onNodeWithTag("screen.timeline.message").assertDoesNotExist()
    }

    /** The items show icons only (2026-10-17): no text in the bar or the rail, each label is the item's description. */
    @Test
    fun navigationItemsShowIconsWithoutLabelsInBothLayouts() = runComposeUiTest {
        var layoutMode by mutableStateOf(LayoutMode.BottomBar)
        withShellLocals { NavigationShellContent(ShellUiState(), layoutMode, onSelect = {}, onBack = {}) }
        val textNodes = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
        for ((mode, chrome) in listOf(LayoutMode.BottomBar to "chrome.bottomBar", LayoutMode.Rail to "chrome.rail")) {
            layoutMode = mode
            waitForIdle()
            onAllNodes(hasAnyAncestor(hasTestTag(chrome)) and textNodes, useUnmergedTree = true).assertCountEquals(0)
            for (destination in Destination.entries) {
                onNodeWithTag("nav.item.${destination.id}").assertContentDescriptionEquals(destination.label)
            }
        }
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
        onNodeWithTag("nav.item.timeline").assertIsNotSelected()
        onNodeWithTag("screen.projects").assertIsDisplayed()
    }

    @Test
    fun reselectingTheActiveItemIsANoOp() = runComposeUiTest {
        var selected: Destination? = null
        withShellLocals { NavigationShellContent(ShellUiState(), LayoutMode.BottomBar, onSelect = { selected = it }, onBack = {}) }
        onNodeWithTag("nav.item.timeline").performClick()
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
        onNodeWithTag("screen.timeline").assertExists()
        mainClock.advanceTimeBy(300)
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("screen.projects").assertIsDisplayed()
        onNodeWithTag("screen.timeline").assertDoesNotExist()
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
        onNodeWithTag("screen.timeline").assertDoesNotExist()
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
