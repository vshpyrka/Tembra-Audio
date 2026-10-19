package audio.tembra.ui.projects

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.projects.components.ProjectCard
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** One card's semantics and input (contracts/projects-library-screen.md; T118): the merged description, tap and Enter open it. */
@OptIn(ExperimentalTestApi::class)
class ProjectCardTest {

    private val id = ProjectId("p1")
    private val card = ProjectCardUi(
        id = id, name = "Live take", description = "Verse and chorus", tempoText = "124 BPM", keyText = "Amin", lengthText = "00:30.96",
        tracksText = "4 tracks", sizeText = "17 MB", relativeTime = "2 m ago", isActive = true, isDemo = false, overview = CardOverview.Empty,
    )

    @Test
    fun theCardIsOneButtonWithTheMergedDescription() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { TembraTheme { ProjectCard(card = card, layout = ProjectsLayout.OneColumn, callbacks = ProjectsCallbacks.None.copy(onOpen = { calls += "open" })) } }
        onNodeWithTag(ProjectsTestTags.card(id))
            .assertHasClickAction()
            .assertContentDescriptionEquals("Live take, Verse and chorus, 124 BPM, Amin, 00:30.96, 4 tracks, 2 m ago, Active")
            .performClick()
        assertEquals(listOf("open"), calls)
    }

    @Test
    fun enterOnTheFocusedCardOpensIt() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { TembraTheme { ProjectCard(card = card, layout = ProjectsLayout.OneColumn, callbacks = ProjectsCallbacks.None.copy(onOpen = { calls += "open" })) } }
        onNodeWithTag(ProjectsTestTags.card(id)).requestFocus().assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("open"), calls)
    }
}
