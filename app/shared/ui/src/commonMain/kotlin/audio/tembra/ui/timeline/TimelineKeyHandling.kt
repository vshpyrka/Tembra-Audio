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

/**
 * The keyboard map of spec FR-057 on the screen root (`compose-focus-navigation`: handle only what is not a normal
 * click or traversal, consume exactly the handled event). Events bubble up from the focused control, so a text
 * field keeps its characters and a focused button its Space; what reaches the root: Space play/pause, Home skip to
 * start, ←/→ one grid step (a bar when the grid is off), Shift+←/→ one bar, S split, Delete/Backspace delete the
 * selected clip, Ctrl/⌘+Z undo, Ctrl/⌘+Shift+Z and Ctrl/⌘+Y redo, +/− zoom, M and O mute and solo the selected track;
 * Escape cancels a clip drag in progress, which returns the clip (feature 011, spec FR-013), and is otherwise left to the
 * shell.
 */
internal fun Modifier.timelineKeyHandling(state: () -> TimelineUiState, callbacks: TimelineCallbacks): Modifier =
    onKeyEvent { event -> handleTimelineKey(event, state(), callbacks) }

internal fun handleTimelineKey(event: KeyEvent, state: TimelineUiState, callbacks: TimelineCallbacks): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    val step = if (event.isShiftPressed) Beats(state.timeSignature.beats.toDouble()) else gridStep(state)
    return when (event.key) {
        Key.Escape if state.drag != null -> consumed { callbacks.onClipDragCancel() }
        Key.Spacebar -> consumed { callbacks.onPlayPause() }
        Key.MoveHome -> consumed { callbacks.onSkipToStart() }
        Key.DirectionLeft -> consumed { callbacks.onSeek((state.positionBeats - step).coerceAtLeastZero()) }
        Key.DirectionRight -> consumed { callbacks.onSeek(state.positionBeats + step) }
        Key.S if !command -> consumed { if (state.canSplit) callbacks.onSplit() }
        Key.Delete, Key.Backspace -> consumed { state.selectedClip?.let { callbacks.onDeleteClip(it.id) } }
        Key.Z if command && event.isShiftPressed -> consumed { callbacks.onRedo() }
        Key.Z if command -> consumed { callbacks.onUndo() }
        Key.Y if command -> consumed { callbacks.onRedo() }
        Key.Plus, Key.Equals, Key.NumPadAdd -> consumed { callbacks.onZoomStep(1) }
        Key.Minus, Key.NumPadSubtract -> consumed { callbacks.onZoomStep(-1) }
        Key.M if !command -> consumed { state.selection.trackIdOrNull?.let(callbacks.onToggleMute) }
        Key.O if !command -> consumed { state.selection.trackIdOrNull?.let(callbacks.onToggleSolo) }
        else -> false
    }
}

private fun gridStep(state: TimelineUiState): Beats =
    state.grid.beatsOf(state.timeSignature) ?: Beats(state.timeSignature.beats.toDouble())

private inline fun consumed(action: () -> Unit): Boolean {
    action()
    return true
}
