package audio.tembra.ui.timeline

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import audio.tembra.core.project.Beats
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets
import audio.tembra.ui.shortcuts.TimelineShortcuts

/**
 * The keyboard map of spec FR-057 on the screen root, read from the shortcut catalogue (feature 011, spec FR-017;
 * `compose-focus-navigation`: handle only what is not a normal click or traversal, consume exactly the handled event).
 * Events bubble up from the focused control, so a text field keeps its characters and a focused button its Space; what
 * reaches the root: Space play/pause, Home skip to start, ←/→ one grid step (a bar when the grid is off), Shift+←/→ one
 * bar, S split, Delete/Backspace delete the selected clip, Ctrl/⌘+Z undo, Ctrl/⌘+Shift+Z and Ctrl/⌘+Y redo, +/− zoom, M
 * and O mute and solo the selected track. On iPad the menu bar serves the ⌘ chords. Escape cancels a clip drag in
 * progress, which returns the clip (feature 011, spec FR-013), and is otherwise left to the shell.
 */
internal fun Modifier.timelineKeyHandling(state: () -> TimelineUiState, callbacks: TimelineCallbacks): Modifier =
    this then TimelineKeysElement(state, callbacks)

private data class TimelineKeysElement(val state: () -> TimelineUiState, val callbacks: TimelineCallbacks) :
    ModifierNodeElement<TimelineKeysNode>() {
    override fun create() = TimelineKeysNode(state, callbacks)

    override fun update(node: TimelineKeysNode) {
        node.state = state
        node.callbacks = callbacks
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "timelineKeyHandling"
    }
}

private class TimelineKeysNode(var state: () -> TimelineUiState, var callbacks: TimelineCallbacks) :
    Modifier.Node(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && state().drag != null) {
            callbacks.onClipDragCancel()
            return true
        }
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Timeline, currentValueOf(LocalShortcutPlatform)) ?: return false
        return ShortcutDispatcher.dispatch(action, ShortcutTargets(timeline = TimelineKeyTargets(state, { callbacks })))
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false
}

/**
 * The Timeline's shortcut actions over the current [state] and [callbacks] (the key handler's and the iPad menu's): a
 * key whose action has no subject now (no clip to split or delete, no track to mute) is still consumed, as feature 009
 * shipped it.
 */
internal class TimelineKeyTargets(
    private val state: () -> TimelineUiState,
    private val callbacks: () -> TimelineCallbacks,
) : TimelineShortcuts {
    override fun playPause() = true.also { callbacks().onPlayPause() }

    override fun skipToStart() = true.also { callbacks().onSkipToStart() }

    override fun step(direction: Int, bar: Boolean): Boolean {
        val now = state()
        val barLength = Beats(now.timeSignature.beats.toDouble())
        val step = if (bar) barLength else now.grid.beatsOf(now.timeSignature) ?: barLength
        callbacks().onSeek(if (direction < 0) (now.positionBeats - step).coerceAtLeastZero() else now.positionBeats + step)
        return true
    }

    override fun split() = true.also { if (state().canSplit) callbacks().onSplit() }

    override fun deleteClip() = true.also { state().selectedClip?.let { callbacks().onDeleteClip(it.id) } }

    override fun undo() = true.also { callbacks().onUndo() }

    override fun redo() = true.also { callbacks().onRedo() }

    override fun zoom(steps: Int) = true.also { callbacks().onZoomStep(steps) }

    override fun toggleMute() = true.also { state().selection.trackIdOrNull?.let(callbacks().onToggleMute) }

    override fun toggleSolo() = true.also { state().selection.trackIdOrNull?.let(callbacks().onToggleSolo) }
}
