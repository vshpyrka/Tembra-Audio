package audio.tembra.ui.mixer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.MixerShortcuts
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets

/**
 * A strip's key map from the catalogue's Mixer scope (feature 012, spec FR-028; feature 011's rule: every key the app
 * answers to is one catalogue entry). Placed on the strip's focus group, so the keys bubble up from any focused control
 * of the strip; the fader and the knob consume their own first. Consumes exactly the events the catalogue matches.
 */
@Composable
internal fun Modifier.mixerKeyHandling(targets: MixerShortcuts): Modifier {
    val platform = LocalShortcutPlatform.current
    return onKeyEvent { event ->
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Mixer, platform) ?: return@onKeyEvent false
        ShortcutDispatcher.dispatch(action, ShortcutTargets(mixer = targets))
    }
}
