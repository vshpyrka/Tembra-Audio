package audio.tembra.ui.metronome

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.MetronomeShortcuts
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets
import kotlinx.coroutines.launch

/**
 * The Metronome's keys on its content root (feature 011, spec FR-018): Space starts and stops, ↑/↓ change the tempo by
 * 1 BPM and with Shift by 10 (held, they repeat; the metronome keeps 40 to 240), T taps the tempo at the frame's time as
 * the tap button does. In the bubbling pass, after the focused control: a focused slider keeps its own arrows.
 */
internal fun Modifier.metronomeKeyHandling(callbacks: MetronomeCallbacks): Modifier = this then MetronomeKeysElement(callbacks)

private data class MetronomeKeysElement(val callbacks: MetronomeCallbacks) : ModifierNodeElement<MetronomeKeysNode>() {
    override fun create() = MetronomeKeysNode(callbacks)

    override fun update(node: MetronomeKeysNode) {
        node.callbacks = callbacks
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "metronomeKeyHandling"
    }
}

private class MetronomeKeysNode(var callbacks: MetronomeCallbacks) :
    Modifier.Node(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode,
    MetronomeShortcuts {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Metronome, currentValueOf(LocalShortcutPlatform)) ?: return false
        return ShortcutDispatcher.dispatch(action, ShortcutTargets(metronome = this))
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun startStop(): Boolean = true.also { callbacks.onTogglePlayback() }

    override fun changeTempo(delta: Int): Boolean = true.also { callbacks.onStep(delta) }

    override fun tapTempo(): Boolean {
        coroutineScope.launch { withFrameNanos { callbacks.onTap(it) } }
        return true
    }
}
