package audio.tembra.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.LibraryQuery
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * The Projects library keeps the user's place across window changes (feature 011, spec FR-007): typed search text and
 * an open card menu survive the window stepping 1200 → 700 → 400 → 1200 dp wide (three, two and one columns).
 */
@OptIn(ExperimentalTestApi::class)
class PlaceAcrossLayoutsProjectsTest {

    private fun card(id: String, name: String) = ProjectCardUi(
        id = ProjectId(id), name = name, description = "", tempoText = "124 BPM", keyText = "—", lengthText = "00:30.96",
        tracksText = "4 tracks", sizeText = "17 MB", relativeTime = "2 m ago", isActive = false, isDemo = false,
        overview = CardOverview(4, byteArrayOf(10, 100, -1, 50), byteArrayOf(0, 1, 2, 3)),
    )

    @Test
    fun searchTextAndAnOpenCardMenuSurviveEveryWidth() = runComposeUiTest {
        var width by mutableStateOf(1200.dp)
        var text by mutableStateOf("")
        val cards = persistentListOf(card("bass", "Bass sketch"), card("band", "Band take"), card("p3", "Ballad"))
        setContent {
            TembraTheme {
                val snackbar = remember { SnackbarHostState() }
                Box(Modifier.requiredSize(width, 800.dp)) {
                    ProjectsScreenContent(
                        state = ProjectsUiState(loaded = true, cards = cards, total = cards.size, query = LibraryQuery(text = text), countText = "3 projects"),
                        callbacks = ProjectsCallbacks.None.copy(onQuery = { text = it }),
                        layout = ProjectsLayout.from(width),
                        snackbarHostState = snackbar,
                    )
                }
            }
        }
        onNodeWithTag(ProjectsTestTags.SEARCH).performTextInput("ba")
        onNodeWithTag("${ProjectsTestTags.card(ProjectId("bass"))}.menu.button").performClick()
        for (step in listOf(1200.dp, 700.dp, 400.dp, 1200.dp)) {
            width = step
            waitForIdle()
            onNodeWithTag(ProjectsTestTags.SEARCH).assertTextContains("ba")
            onNodeWithTag("${ProjectsTestTags.card(ProjectId("bass"))}.menu").assertExists("the card's menu closed at $step")
        }
    }
}
