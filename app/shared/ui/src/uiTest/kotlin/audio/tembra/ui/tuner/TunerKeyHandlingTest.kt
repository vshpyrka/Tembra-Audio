package audio.tembra.ui.tuner

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.shortcuts.PrimaryModifier
import audio.tembra.ui.shortcuts.ShortcutPlatform
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Tuner's keys (feature 011, spec FR-018a): A and E switch to Auto Listen and By Ear; in By Ear a number plays the
 * string with that number on its card (1 is string I, the highest, the last card), up to the instrument's string count;
 * Space stops the tone; L toggles Loop Tone.
 */
@OptIn(ExperimentalTestApi::class)
class TunerKeyHandlingTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private val callbacks = TunerCallbacks.None.copy(
        onMode = { calls += "mode(${it.name})" }, onAudition = { calls += "audition($it)" },
        onStopAudition = { calls += "stop" }, onLoopTone = { calls += "loop($it)" },
    )

    private fun ComposeUiTest.setRoot(state: TunerUiState) {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.size(200.dp).testTag("root").focusable().tunerKeyHandling({ state }, callbacks, ShortcutPlatform(PrimaryModifier.Ctrl, menuServesPrimaryChords = false)))
            }
        }
        enterKeyboardMode(manager)
        onNodeWithTag("root").requestFocus()
    }

    private fun byEar(instrument: Instrument, strings: GuitarStringCount = GuitarStringCount.Six) = TunerUiState(
        loaded = true, mode = TunerMode.ByEar, instrument = instrument, guitarStrings = strings,
        preset = TuningPresets.default(instrument, strings), presets = TuningPresets.forInstrument(instrument, strings),
    )

    @Test
    fun aAndESwitchModes() = runComposeUiTest {
        setRoot(TunerUiState(loaded = true))
        onNodeWithTag("root").performKeyInput { pressKey(Key.E); pressKey(Key.A) }
        assertEquals(listOf("mode(ByEar)", "mode(AutoListen)"), calls)
    }

    @Test
    fun numbersPlayTheStringsByTheirCardNumbers() = runComposeUiTest {
        setRoot(byEar(Instrument.Guitar))
        onNodeWithTag("root").performKeyInput { pressKey(Key.One); pressKey(Key.Six); pressKey(Key.Seven) }
        // Six strings: string I is the last card (index 5), string VI the first (index 0); there is no string 7.
        assertEquals(listOf("audition(5)", "audition(0)"), calls)
    }

    @Test
    fun aSevenStringGuitarHasAStringSeven() = runComposeUiTest {
        setRoot(byEar(Instrument.Guitar, GuitarStringCount.Seven))
        onNodeWithTag("root").performKeyInput { pressKey(Key.Seven) }
        assertEquals(listOf("audition(0)"), calls)
    }

    @Test
    fun fourIsTheLastStringOnACello() = runComposeUiTest {
        setRoot(byEar(Instrument.Cello))
        onNodeWithTag("root").performKeyInput { pressKey(Key.Four); pressKey(Key.Five) }
        assertEquals(listOf("audition(0)"), calls)
    }

    @Test
    fun spaceStopsAndLTogglesLoop() = runComposeUiTest {
        setRoot(byEar(Instrument.Guitar).copy(loopTone = true))
        onNodeWithTag("root").performKeyInput { pressKey(Key.Spacebar); pressKey(Key.L) }
        assertEquals(listOf("stop", "loop(false)"), calls)
    }

    @Test
    fun numbersDoNothingInAutoListen() = runComposeUiTest {
        setRoot(TunerUiState(loaded = true))
        onNodeWithTag("root").performKeyInput { pressKey(Key.One) }
        assertEquals(emptyList(), calls)
    }
}
