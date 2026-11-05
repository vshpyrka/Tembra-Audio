package audio.tembra.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.metronome.MetronomeCallbacks
import audio.tembra.ui.metronome.MetronomeLayout
import audio.tembra.ui.metronome.MetronomeScreenContent
import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.metronome.ScriptedBeatTelemetry
import audio.tembra.ui.profile.ProfileScreen
import audio.tembra.ui.projects.ProjectsCallbacks
import audio.tembra.ui.projects.ProjectsLayout
import audio.tembra.ui.projects.ProjectsScreenContent
import audio.tembra.ui.projects.ProjectsUiState
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineScreenContent
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.tuner.ScriptedTelemetry
import audio.tembra.ui.tuner.TunerCallbacks
import audio.tembra.ui.tuner.TunerLayout
import audio.tembra.ui.tuner.TunerScreenContent
import audio.tembra.ui.tuner.TunerUiState
import kotlin.test.Test

/**
 * Keyboard focus is in the window at a predictable place (feature 011, spec FR-024): each destination's content root
 * takes focus once it appears, so the first Tab moves to its first control without a click. The Tab step runs where
 * the lane declares keyboard input mode (`KeyboardInputMode.<platform>.kt`); the root's focus is checked everywhere.
 */
@OptIn(ExperimentalTestApi::class)
class InitialFocusTest {

    private var manager: InputModeManager? = null

    private fun ComposeUiTest.assertRootFocusedThenTabMovesIn(root: String, content: @Composable () -> Unit) {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.requiredSize(1200.dp, 800.dp)) { content() }
            }
        }
        waitForIdle()
        onNodeWithTag(root).assertIsFocused()
        if (!enterKeyboardMode(manager)) return
        onNodeWithTag(root).performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        onNodeWithTag(root).assertIsNotFocused()
        onNode(isFocused() and hasAnyAncestor(hasTestTag(root))).assertExists("Tab did not move focus into $root")
    }

    @Test
    fun timelineRootIsFocused() = runComposeUiTest {
        assertRootFocusedThenTabMovesIn("screen.timeline") {
            TimelineScreenContent(
                state = TimelineUiState(loaded = true),
                callbacks = TimelineCallbacks.None,
                telemetry = ScriptedTransportTelemetry.stopped(0.0),
                layout = TimelineLayout.Expanded,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun tunerRootIsFocused() = runComposeUiTest {
        assertRootFocusedThenTabMovesIn("screen.tuner") {
            TunerScreenContent(state = TunerUiState(loaded = true), callbacks = TunerCallbacks.None, telemetry = ScriptedTelemetry(), layout = TunerLayout.Wide)
        }
    }

    @Test
    fun metronomeRootIsFocused() = runComposeUiTest {
        assertRootFocusedThenTabMovesIn("screen.metronome") {
            MetronomeScreenContent(
                state = MetronomeUiState(loaded = true),
                callbacks = MetronomeCallbacks.None,
                telemetry = ScriptedBeatTelemetry(),
                layout = MetronomeLayout.Wide,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun projectsRootIsFocused() = runComposeUiTest {
        assertRootFocusedThenTabMovesIn("screen.projects") {
            ProjectsScreenContent(
                state = ProjectsUiState(loaded = true),
                callbacks = ProjectsCallbacks.None,
                layout = ProjectsLayout.ThreeColumns,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun profileRootIsFocused() = runComposeUiTest {
        // Profile has no control yet (its "Keyboard shortcuts" row comes with the shortcut sheet): only the root's focus.
        setContent { TembraTheme { Box(Modifier.requiredSize(1200.dp, 800.dp)) { ProfileScreen() } } }
        waitForIdle()
        onNodeWithTag("screen.profile").assertIsFocused()
    }
}
