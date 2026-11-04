package audio.tembra.ui.projects

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.PrimaryModifier
import audio.tembra.ui.shortcuts.ShortcutPlatform
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The library's keys (feature 011, spec FR-021): Ctrl/⌘+F focuses the search field, Ctrl/⌘+N starts a new project. */
@OptIn(ExperimentalTestApi::class)
class ProjectsKeyHandlingTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null

    private fun ComposeUiTest.setScreen() {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                // Ctrl on both lanes: on iPad the menu bar serves these chords, so the test takes Android's way.
                CompositionLocalProvider(LocalShortcutPlatform provides ShortcutPlatform(PrimaryModifier.Ctrl, menuServesPrimaryChords = false)) {
                    ProjectsScreenContent(
                        state = ProjectsUiState(loaded = true),
                        callbacks = ProjectsCallbacks.None.copy(onNewProject = { calls += "new" }),
                        layout = ProjectsLayout.TwoColumns,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        enterKeyboardMode(manager)
        waitForIdle()
    }

    private fun ComposeUiTest.primary(key: Key) {
        onNodeWithTag(ProjectsTestTags.ROOT).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(key)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    @Test
    fun ctrlFFocusesTheSearchField() = runComposeUiTest {
        setScreen()
        primary(Key.F)
        onNodeWithTag(ProjectsTestTags.SEARCH).assertIsFocused()
    }

    @Test
    fun ctrlNStartsANewProject() = runComposeUiTest {
        setScreen()
        primary(Key.N)
        assertEquals(listOf("new"), calls)
    }
}
