package audio.tembra.ui.timeline

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import audio.tembra.core.project.Beats
import audio.tembra.ui.transport.RecordButtonState

/**
 * The keyboard map of spec FR-057 on the screen root (`compose-focus-navigation`: handle only what is not a normal
 * click or traversal, consume exactly the handled event). Events bubble up from the focused control, so a text
 * field keeps its characters and a focused button its Space; what reaches the root: Space play/pause, Home skip to
 * start, ←/→ one grid step (a bar when the grid is off), Shift+←/→ one bar, S split, Delete/Backspace delete the
 * selected clip, Ctrl/⌘+Z undo, Ctrl/⌘+Shift+Z and Ctrl/⌘+Y redo, +/− zoom, M and O mute and solo the selected track.
 * Feature 010 (FR-028): A arms the selected track, R records, I toggles monitoring, Escape cancels a count-in; space
 * during a take pauses (the take ends); the keys of actions FR-018 makes unavailable do nothing while a take runs.
 */
internal fun Modifier.timelineKeyHandling(state: () -> TimelineUiState, callbacks: TimelineCallbacks): Modifier =
    onKeyEvent { event -> handleTimelineKey(event, state(), callbacks) }

internal fun handleTimelineKey(event: KeyEvent, state: TimelineUiState, callbacks: TimelineCallbacks): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    val step = if (event.isShiftPressed) Beats(state.timeSignature.beats.toDouble()) else gridStep(state)
    val seeking = state.transport.seekEnabled
    val editing = !state.takeRunning
    return when (event.key) {
        Key.Spacebar -> consumed { callbacks.onPlayPause() }
        Key.MoveHome -> consumed { if (seeking) callbacks.onSkipToStart() }
        Key.DirectionLeft -> consumed { if (seeking) callbacks.onSeek((state.positionBeats - step).coerceAtLeastZero()) }
        Key.DirectionRight -> consumed { if (seeking) callbacks.onSeek(state.positionBeats + step) }
        Key.S if !command -> consumed { if (state.canSplit) callbacks.onSplit() }
        Key.Delete, Key.Backspace -> consumed { if (editing) state.selectedClip?.let { callbacks.onDeleteClip(it.id) } }
        Key.Z if command && event.isShiftPressed -> consumed { if (state.canRedo) callbacks.onRedo() }
        Key.Z if command -> consumed { if (state.canUndo) callbacks.onUndo() }
        Key.Y if command -> consumed { if (state.canRedo) callbacks.onRedo() }
        Key.Plus, Key.Equals, Key.NumPadAdd -> consumed { callbacks.onZoomStep(1) }
        Key.Minus, Key.NumPadSubtract -> consumed { callbacks.onZoomStep(-1) }
        Key.M if !command -> consumed { state.selection.trackIdOrNull?.let(callbacks.onToggleMute) }
        Key.O if !command -> consumed { state.selection.trackIdOrNull?.let(callbacks.onToggleSolo) }
        // Feature 010 (FR-028): arm, record, monitor; Escape only during a count-in, so dialogs and menus keep theirs.
        Key.A if !command -> consumed { if (state.canArm) state.selection.trackIdOrNull?.let(callbacks.onArm) }
        Key.R if !command -> consumed { callbacks.onRecord() }
        Key.I if !command -> consumed { callbacks.onMonitorToggle() }
        Key.Escape -> if (state.recordPhase == RecordButtonState.CountIn) consumed { callbacks.onRecord() } else false
        else -> false
    }
}

private fun gridStep(state: TimelineUiState): Beats =
    state.grid.beatsOf(state.timeSignature) ?: Beats(state.timeSignature.beats.toDouble())

private inline fun consumed(action: () -> Unit): Boolean {
    action()
    return true
}
