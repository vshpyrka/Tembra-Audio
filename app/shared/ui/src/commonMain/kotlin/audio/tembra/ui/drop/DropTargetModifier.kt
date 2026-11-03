package audio.tembra.ui.drop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Makes this layout take files dragged onto the app (feature 011, spec FR-026; contracts/drop-import.md): the platform's
 * [LocalDroppedFilesReader] decides what is accepted and reads it; [state] follows the hover in positions local to this
 * layout and plans the drop, whose [DropOutcome] goes to [onDrop]. Without a reader (previews, tests) it adds nothing.
 * The read starts inside the drop callback, so the platform's work that needs a live drop event runs before it returns.
 */
@Composable
fun Modifier.audioDropTarget(state: AudioDropState, onDrop: (DropOutcome) -> Unit): Modifier {
    val reader = LocalDroppedFilesReader.current
    if (reader === NoDroppedFiles) return this
    val scope = rememberCoroutineScope()
    val currentOnDrop by rememberUpdatedState(onDrop)
    val layout = remember { LayoutHolder() }
    val target = remember(state, reader) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = state.entered(layout.local(event), reader.audioCount(event))

            override fun onMoved(event: DragAndDropEvent) = state.moved(layout.local(event))

            override fun onExited(event: DragAndDropEvent) = state.exited()

            override fun onEnded(event: DragAndDropEvent) = state.ended()

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val position = layout.local(event)
                scope.launch(CoroutineName("drop"), start = CoroutineStart.UNDISPATCHED) {
                    currentOnDrop(state.dropped(position) { reader.read(event) })
                }
                return true
            }
        }
    }
    return onGloballyPositioned { layout.coordinates = it }
        .dragAndDropTarget(shouldStartDragAndDrop = reader::accepts, target = target)
}

/** Where the target sits, to turn the event's root position into a local one. Read in callbacks only. */
private class LayoutHolder {
    var coordinates: LayoutCoordinates? = null

    fun local(event: DragAndDropEvent): Offset? = event.rootPosition()?.let { it - (coordinates?.positionInRoot() ?: Offset.Zero) }
}
