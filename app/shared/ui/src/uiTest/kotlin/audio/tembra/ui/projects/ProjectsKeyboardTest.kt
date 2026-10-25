package audio.tembra.ui.projects

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tab walks search → chips → sort → cards and Enter opens the focused card (contracts/projects-library-screen.md; T122, US8).
 * Runs where the lane declares keyboard input mode (`KeyboardInputMode.<platform>.kt`): the iOS simulator; the Android device
 * lane stays in touch mode and skips by that declaration.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsKeyboardTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private val first = ProjectId("p1")
    private val cards = persistentListOf(
        ProjectCardUi(first, "Alpha", "", "124 BPM", "—", "00:30.96", "4 tracks", "17 MB", "2 m ago", isActive = false, isDemo = false, overview = CardOverview.Empty),
        ProjectCardUi(ProjectId("p2"), "Beta", "", "120 BPM", "—", "00:10.00", "1 track", "2 MB", "Yesterday 22:40", isActive = false, isDemo = false, overview = CardOverview.Empty),
    )

    /** Composes the screen and enters keyboard mode; false when this lane declares none. */
    private fun ComposeUiTest.setScreen(): Boolean {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                ProjectsScreenContent(
                    state = ProjectsUiState(loaded = true, cards = cards, total = 2, countText = "2 projects"),
                    callbacks = ProjectsCallbacks.None.copy(onOpen = { calls += "open(${it.value})" }),
                    layout = ProjectsLayout.OneColumn,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        return enterKeyboardMode(manager)
    }

    /** One pass of Tabs from the search field; the first step at which each tag is focused fixes its place in the order. */
    private fun ComposeUiTest.focusOrder(vararg tags: String, tabs: Int = 16): List<Int> {
        val firstSeen = mutableMapOf<String, Int>()
        repeat(tabs) { step ->
            onNodeWithTag(ProjectsTestTags.ROOT).performKeyInput { pressKey(Key.Tab) }
            for (tag in tags) {
                if (tag !in firstSeen && isFocused().matches(onNodeWithTag(tag).fetchSemanticsNode())) firstSeen[tag] = step
            }
        }
        return tags.map { firstSeen[it] ?: throw AssertionError("$it never received focus within $tabs tabs: $firstSeen") }
    }

    @Test
    fun tabWalksTheControlsInOrderAndEnterOpensTheFocusedCard() = runComposeUiTest {
        if (!setScreen()) return@runComposeUiTest
        onNodeWithTag(ProjectsTestTags.SEARCH).requestFocus()
        val order = focusOrder("${ProjectsTestTags.FILTER}.all", ProjectsTestTags.SORT, ProjectsTestTags.card(first))
        assertEquals(order.sorted(), order, "focus walks chips, sort, cards in that order: $order")
        onNodeWithTag(ProjectsTestTags.card(first)).requestFocus()
        onNodeWithTag(ProjectsTestTags.card(first)).performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("open(p1)"), calls)
    }
}
