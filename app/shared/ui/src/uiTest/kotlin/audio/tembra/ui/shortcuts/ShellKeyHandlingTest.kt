package audio.tembra.ui.shortcuts

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.designsystem.keyPressesTypeText
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.projects.components.LibrarySearchField
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shell's own chords (feature 011, spec FR-017, FR-022, FR-023; contracts/keyboard-shortcuts.md rules 1, 5, 6):
 * the primary modifier with 1 to 5 opens the destinations in navigation order, with / the shortcut sheet; none fires
 * while a text field has focus (its characters are typed); on iPad the menu bar serves the Command chords, so the
 * Compose handler leaves them alone there.
 */
@OptIn(ExperimentalTestApi::class)
class ShellKeyHandlingTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private val shell = object : ShellShortcuts {
        override fun open(destination: Destination): Boolean = true.also { calls += "open(${destination.id})" }
        override fun showShortcuts(): Boolean = true.also { calls += "sheet" }
        override fun closeOverlay(): Boolean = false
    }

    private fun ComposeUiTest.setShell(menuServesPrimaryChords: Boolean = false) {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                val textInput = remember { TextInputFocus() }
                var text by remember { mutableStateOf("") }
                CompositionLocalProvider(LocalTextInputFocus provides textInput) {
                    Column(
                        Modifier.size(600.dp).testTag("root").focusable()
                            .shellKeyHandling(shell, textInput, ShortcutPlatform(PrimaryModifier.Ctrl, menuServesPrimaryChords)),
                    ) {
                        LibrarySearchField(text = text, onText = { text = it })
                        Box(Modifier.size(10.dp))
                    }
                }
            }
        }
        enterKeyboardMode(manager)
        onNodeWithTag("root").requestFocus()
    }

    private fun ComposeUiTest.chord(key: Key, target: String = "root") {
        onNodeWithTag(target).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(key)
            keyUp(Key.CtrlLeft)
        }
    }

    @Test
    fun ctrlOneToFiveOpenTheDestinationsAndCtrlSlashTheSheet() = runComposeUiTest {
        setShell()
        for (key in listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)) chord(key)
        chord(Key.Slash)
        assertEquals(listOf("open(timeline)", "open(tuner)", "open(metronome)", "open(projects)", "open(profile)", "sheet"), calls)
    }

    @Test
    fun aFocusedTextFieldTypesItsCharactersAndTheChordsWait() = runComposeUiTest {
        setShell()
        onNodeWithTag("projects.search").requestFocus()
        onNodeWithTag("projects.search").performKeyInput { pressKey(Key.Two); pressKey(Key.Slash) }
        chord(Key.Two, target = "projects.search")
        assertEquals(emptyList(), calls)
        // The characters themselves, where this lane's key presses type (declared per platform).
        if (keyPressesTypeText) {
            onNodeWithTag("projects.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("2/")))
        }
    }

    @Test
    fun whereTheMenuBarServesTheChordsComposeLeavesThem() = runComposeUiTest {
        setShell(menuServesPrimaryChords = true)
        chord(Key.One)
        chord(Key.Slash)
        assertEquals(emptyList(), calls)
    }
}
