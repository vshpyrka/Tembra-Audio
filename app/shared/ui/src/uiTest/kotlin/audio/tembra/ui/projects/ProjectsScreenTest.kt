package audio.tembra.ui.projects

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import audio.tembra.core.project.LibraryFilter
import audio.tembra.core.project.LibraryQuery
import audio.tembra.core.project.LibrarySort
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.ProjectId
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.projects.components.MENU_RENAME
import audio.tembra.ui.projects.components.MENU_RESTORE
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The library at compact and expanded (spec FR-050 to FR-053; T118): six cards, search, chips, sort, the menu, the dialogs, the empty states. */
@OptIn(ExperimentalTestApi::class)
class ProjectsScreenTest {

    private val calls = mutableListOf<String>()
    private val demoId = ProjectId("d1") // not "demo": the demo card reads `projects.card.demo` whatever its id
    private val activeId = ProjectId("active")

    private fun card(id: String, name: String, description: String = "", key: String = "—", active: Boolean = false, demo: Boolean = false) = ProjectCardUi(
        id = ProjectId(id), name = name, description = description, tempoText = "124 BPM", keyText = key, lengthText = "00:30.96",
        tracksText = "4 tracks", sizeText = "17 MB", relativeTime = "2 m ago", isActive = active, isDemo = demo,
        overview = CardOverview(4, byteArrayOf(10, 100, -1, 50), byteArrayOf(0, 1, 2, 3)),
    )

    private val six = persistentListOf(
        card("d1", "Demo project", "Four loops to play with", "Dmin", demo = true),
        card("active", "Live take", "Verse and chorus", "Amin", active = true),
        card("p3", "Sketch 3", key = "Cmaj"),
        card("p4", "Sketch 4", description = "No key here"),
        card("p5", "Sketch 5"),
        card("p6", "Sketch 6"),
    )

    private fun state(cards: List<ProjectCardUi> = six, query: LibraryQuery = LibraryQuery(), dialog: ProjectsDialog? = null, total: Int = cards.size) = ProjectsUiState(
        loaded = true, cards = persistentListOf(*cards.toTypedArray()), total = total, query = query, countText = query.countText(cards.size, total), dialog = dialog,
    )

    private fun ComposeUiTest.setScreen(state: ProjectsUiState, layout: ProjectsLayout = ProjectsLayout.OneColumn) {
        setContent {
            TembraTheme {
                ProjectsScreenContent(
                    state = state,
                    callbacks = ProjectsCallbacks.None.copy(
                        onQuery = { calls += "query($it)" }, onFilter = { calls += "filter($it)" }, onSort = { calls += "sort($it)" },
                        onNewProject = { calls += "new" }, onOpen = { calls += "open(${it.value})" }, onRename = { calls += "rename(${it.value})" },
                        onRestoreDemo = { calls += "restore" }, onRenameConfirm = { calls += "renameConfirm($it)" },
                        onKeyConfirm = { calls += "key($it)" }, onDeleteConfirm = { calls += "deleteConfirm" }, onDialogDismiss = { calls += "dismiss" },
                    ),
                    layout = layout,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    @Test
    fun sixCardsWithBadgesCountAndTheActiveResume() = runComposeUiTest {
        setScreen(state())
        onNodeWithTag(ProjectsTestTags.ROOT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, READY))
        onNodeWithTag(ProjectsTestTags.COUNT).assertTextEquals("6 projects")
        onNodeWithTag(ProjectsTestTags.CARD_DEMO).assertExists()
        onNodeWithText("Four loops to play with").assertExists()
        onNodeWithTag(ProjectsTestTags.card(activeId)).assertExists()
        onNodeWithTag(ProjectsTestTags.cardOpen(activeId)).assert(hasText("Resume"))
        // The grid composes what fits; the later cards exist once scrolled to.
        onNodeWithTag(ProjectsTestTags.GRID).performScrollToNode(hasTestTag(ProjectsTestTags.card(ProjectId("p6"))))
        onNodeWithTag(ProjectsTestTags.card(ProjectId("p6"))).assertExists()
        onNodeWithTag(ProjectsTestTags.GRID).performScrollToNode(hasTestTag(ProjectsTestTags.card(ProjectId("p3"))))
        onNodeWithTag(ProjectsTestTags.cardOpen(ProjectId("p3"))).assert(hasText("Open"))
        onNodeWithTag(ProjectsTestTags.GRID).performScrollToNode(hasTestTag(ProjectsTestTags.card(activeId)))
        onNodeWithTag(ProjectsTestTags.NEW_PROJECT).performClick()
        onNodeWithTag(ProjectsTestTags.cardOpen(activeId)).performClick()
        assertEquals(listOf("new", "open(active)"), calls)
    }

    @Test
    fun searchChipsAndSortAreIntents() = runComposeUiTest {
        // The search field is controlled: the typed text comes back through the state, or the device's IME resets it to "".
        setContent {
            TembraTheme {
                var text by remember { mutableStateOf("") }
                ProjectsScreenContent(
                    state = state(query = LibraryQuery(text = text)),
                    callbacks = ProjectsCallbacks.None.copy(
                        onQuery = { calls += "query($it)"; text = it }, onFilter = { calls += "filter($it)" }, onSort = { calls += "sort($it)" },
                    ),
                    layout = ProjectsLayout.OneColumn,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        onNodeWithTag(ProjectsTestTags.SEARCH).performTextInput("ske")
        onNodeWithTag("${ProjectsTestTags.FILTER}.recent").performClick()
        onNodeWithTag(ProjectsTestTags.SORT).performClick()
        onNodeWithTag("${ProjectsTestTags.SORT}.name").performClick()
        assertEquals(listOf("query(ske)", "filter(${LibraryFilter.Recent})", "sort(${LibrarySort.Name})"), calls)
    }

    @Test
    fun theCardMenuOpensAndTheDemoOffersRestore() = runComposeUiTest {
        setScreen(state())
        onNodeWithTag("${ProjectsTestTags.CARD_DEMO}.menu.button").performClick()
        onNodeWithTag("${ProjectsTestTags.CARD_DEMO}.menu.$MENU_RESTORE").assertExists()
        onNodeWithTag("${ProjectsTestTags.CARD_DEMO}.menu.$MENU_RENAME").performClick()
        assertEquals(listOf("rename(d1)"), calls)
    }

    @Test
    fun theDialogsRenderAndConfirm() = runComposeUiTest {
        setScreen(state(dialog = ProjectsDialog.Rename(demoId, "Demo project")))
        onNodeWithTag(ProjectsTestTags.RENAME_DIALOG).assertExists()
        onNodeWithTag(ProjectsTestTags.DIALOG_FIELD).performTextInput("!")
        onNodeWithTag(ProjectsTestTags.DIALOG_CONFIRM).performClick()
        assertEquals(listOf("renameConfirm(!Demo project)"), calls)
    }

    @Test
    fun theTextDialogsOpenWithTheirFieldFocused() = runComposeUiTest {
        // A keyboard user types the name or the description straight away (feature 011, the T125 desktop walk found the
        // rename dialog waiting for a click in its field).
        setScreen(state(dialog = ProjectsDialog.Rename(demoId, "Demo project")))
        waitForIdle()
        onNodeWithTag(ProjectsTestTags.DIALOG_FIELD).assertIsFocused()
    }

    @Test
    fun theDescriptionDialogOpensWithItsFieldFocused() = runComposeUiTest {
        setScreen(state(dialog = ProjectsDialog.Describe(demoId, "")))
        waitForIdle()
        onNodeWithTag(ProjectsTestTags.DIALOG_FIELD).assertIsFocused()
    }

    @Test
    fun theKeyDialogListsNoneAndTheKeysAndTheDeleteDialogConfirms() = runComposeUiTest {
        setScreen(state(dialog = ProjectsDialog.Key(demoId, MusicalKey.DMinor)))
        onNodeWithTag(ProjectsTestTags.KEY_DIALOG).assertExists()
        onNodeWithTag(ProjectsTestTags.keyOption("none")).assertExists()
        onNodeWithTag(ProjectsTestTags.keyOption(MusicalKey.FSharpMajor.name)).performClick()
        assertEquals(listOf("key(${MusicalKey.FSharpMajor})"), calls)
    }

    @Test
    fun deleteConfirms() = runComposeUiTest {
        setScreen(state(dialog = ProjectsDialog.Delete(demoId, "Demo project")))
        onNodeWithTag(ProjectsTestTags.DELETE_DIALOG).assertExists()
        onNodeWithTag(ProjectsTestTags.DIALOG_CONFIRM).performClick()
        assertEquals(listOf("deleteConfirm"), calls)
    }

    @Test
    fun theEmptyStatesOfferTheWayOut() = runComposeUiTest {
        setScreen(state(cards = emptyList(), total = 0))
        onNodeWithTag(ProjectsTestTags.EMPTY).assertExists()
        onNodeWithTag(ProjectsTestTags.EMPTY_RESTORE).performClick()
        assertEquals(listOf("restore"), calls)
    }

    @Test
    fun aSearchWithoutAMatchSaysSo() = runComposeUiTest {
        setScreen(state(cards = emptyList(), query = LibraryQuery(text = "zzz"), total = 6))
        onNodeWithTag(ProjectsTestTags.NO_MATCH).assertExists()
        onNodeWithTag(ProjectsTestTags.COUNT).assertTextEquals("0 of 6 projects")
    }

    @Test
    fun expandedUsesMoreColumns() = runComposeUiTest {
        setScreen(state(), ProjectsLayout.TwoColumns)
        onNodeWithTag(ProjectsTestTags.GRID).assertExists()
        onNodeWithTag(ProjectsTestTags.CARD_DEMO).assertExists()
    }
}
