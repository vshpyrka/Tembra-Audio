package audio.tembra.ui.tuner

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutPlatform
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets

/**
 * The Tuner's keys on its content root (feature 011, spec FR-018a): A and E switch to Auto Listen and By Ear; in By Ear
 * a number plays the string with that number on its card (1 is string I, the highest, which is the last card; numbers
 * above the instrument's string count do nothing), Space stops the tone and L toggles Loop Tone. In the bubbling pass,
 * after the focused control.
 */
internal fun Modifier.tunerKeyHandling(state: () -> TunerUiState, callbacks: TunerCallbacks, platform: ShortcutPlatform): Modifier =
    onKeyEvent { event ->
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Tuner, platform) ?: return@onKeyEvent false
        ShortcutDispatcher.dispatch(action, ShortcutTargets(tuner = TunerKeyTargets(state(), callbacks)))
    }
