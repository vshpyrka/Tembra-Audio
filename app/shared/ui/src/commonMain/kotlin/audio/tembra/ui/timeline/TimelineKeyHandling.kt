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
import audio.tembra.ui.transport.RecordButtonState

/**
 * The keyboard map of spec FR-057 on the screen root, read from the shortcut catalogue (feature 011, spec FR-017;
 * `compose-focus-navigation`: handle only what is not a normal click or traversal, consume exactly the handled event).
 * Events bubble up from the focused control, so a text field keeps its characters and a focused button its Space; what
 * reaches the root: Space play/pause, Home skip to start, ←/→ one grid step (a bar when the grid is off), Shift+←/→ one
 * bar, S split, Delete/Backspace delete the selected clip, Ctrl/⌘+Z undo, Ctrl/⌘+Shift+Z and Ctrl/⌘+Y redo, +/− zoom, M
 * and O mute and solo the selected track. Feature 010 (FR-028): A arms the selected track, R records, I toggles
 * monitoring; space during a take pauses (the take ends); the keys of actions FR-018 makes unavailable do nothing while a
 * take runs. On iPad the menu bar serves the ⌘ chords. Escape cancels a clip drag in progress, which returns the clip
 * (feature 011, spec FR-013), or a count-in (feature 010), and is otherwise left to the shell.
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
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            if (state().drag != null) {
                callbacks.onClipDragCancel()
                return true
            }
            // Only during a count-in, so dialogs and menus keep their Escape (feature 010, FR-028).
            if (state().recordPhase == RecordButtonState.CountIn) {
                callbacks.onRecord()
                return true
            }
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

    override fun skipToStart() = true.also { if (state().transport.seekEnabled) callbacks().onSkipToStart() }

    override fun step(direction: Int, bar: Boolean): Boolean {
        val now = state()
        if (!now.transport.seekEnabled) return true
        val barLength = Beats(now.timeSignature.beats.toDouble())
        val step = if (bar) barLength else now.grid.beatsOf(now.timeSignature) ?: barLength
        callbacks().onSeek(if (direction < 0) (now.positionBeats - step).coerceAtLeastZero() else now.positionBeats + step)
        return true
    }

    override fun split() = true.also { if (state().canSplit) callbacks().onSplit() }

    // While a take runs these are unavailable (feature 010, FR-018): the keys stay consumed and do nothing.
    override fun deleteClip() = true.also { if (!state().takeRunning) state().selectedClip?.let { callbacks().onDeleteClip(it.id) } }

    override fun undo() = true.also { if (state().canUndo) callbacks().onUndo() }

    override fun redo() = true.also { if (state().canRedo) callbacks().onRedo() }

    override fun zoom(steps: Int) = true.also { callbacks().onZoomStep(steps) }

    override fun toggleMute() = true.also { state().selection.trackIdOrNull?.let(callbacks().onToggleMute) }

    override fun toggleSolo() = true.also { state().selection.trackIdOrNull?.let(callbacks().onToggleSolo) }

    override fun arm() = true.also { if (state().canArm) state().selection.trackIdOrNull?.let(callbacks().onArm) }

    override fun record() = true.also { callbacks().onRecord() }

    override fun toggleMonitor() = true.also { callbacks().onMonitorToggle() }

    // Feature 012 (FR-017, FR-029): X opens and closes the mixer once a project is in.
    override fun toggleMixer() = true.also { if (state().loaded) callbacks().onMixerToggle() }
}
