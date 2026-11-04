package audio.tembra.ui.designsystem

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.launch

/** The [FocusMemory] of the nearest [focusRestoringRoot], for the controls under it. */
private val ModifierLocalFocusMemory = modifierLocalOf<FocusMemory?> { null }

/**
 * A destination's content root that restores keyboard focus after its controls are rebuilt (feature 011, spec FR-007,
 * FR-009): place it before the root's own focus target (`focusRequester(...).focusable()`), with a [memory] remembered
 * above the destination's layout branches.
 */
internal fun Modifier.focusRestoringRoot(memory: FocusMemory): Modifier = this then FocusRestoringRootElement(memory)

/**
 * The semantic identity of a focusable control, for [focusRestoringRoot]: place it before the control's focus target
 * (`clickable`, `selectable`, `focusable`). A null [id] adds nothing.
 */
internal fun Modifier.focusIdentity(id: String?): Modifier = if (id == null) this else this then FocusIdentityElement(id)

private data class FocusRestoringRootElement(val memory: FocusMemory) : ModifierNodeElement<FocusRestoringRootNode>() {
    override fun create() = FocusRestoringRootNode(memory)

    override fun update(node: FocusRestoringRootNode) {
        node.update(memory)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRestoringRoot"
    }
}

internal class FocusRestoringRootNode(private var memory: FocusMemory) :
    Modifier.Node(),
    ModifierLocalModifierNode,
    FocusRequesterModifierNode,
    FocusEventModifierNode {

    override val providedValues = modifierLocalMapOf(ModifierLocalFocusMemory to memory)
    private var hasFocusWithin = false

    fun update(next: FocusMemory) {
        if (next === memory) return
        if (memory.root === this) memory.root = null
        memory = next
        provide(ModifierLocalFocusMemory, next)
        next.root = this
    }

    override fun onAttach() {
        memory.root = this
        if (memory.pending != null) scheduleRestore()
    }

    override fun onDetach() {
        if (memory.root === this) memory.root = null
    }

    override fun onFocusEvent(focusState: FocusState) {
        hasFocusWithin = focusState.hasFocus
    }

    /** A frame later, when the new form's controls are attached: the same identity, else the root. */
    fun scheduleRestore() {
        if (!isAttached) return
        coroutineScope.launch {
            withFrameNanos { }
            val id = memory.takePending() ?: return@launch
            val control = memory.controlFor(id)
            if (control != null && control.requestFocus()) return@launch
            if (!hasFocusWithin) requestFocus()
        }
    }
}

private data class FocusIdentityElement(val id: String) : ModifierNodeElement<FocusIdentityNode>() {
    override fun create() = FocusIdentityNode(id)

    override fun update(node: FocusIdentityNode) {
        node.update(id)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusIdentity"
        properties["id"] = id
    }
}

internal class FocusIdentityNode(private var id: String) :
    Modifier.Node(),
    ModifierLocalModifierNode,
    FocusRequesterModifierNode,
    FocusEventModifierNode {

    private var memory: FocusMemory? = null
    private var focused = false

    fun update(next: String) {
        if (next == id) return
        memory?.unregister(id, this)
        id = next
        memory?.register(id, this)
    }

    override fun onAttach() {
        memory = ModifierLocalFocusMemory.current
        memory?.register(id, this)
    }

    override fun onFocusEvent(focusState: FocusState) {
        // Focus that leaves because this control is being removed is reported in onDetach, which can come before or
        // after this event: a frame's grace keeps the loss attributable to the removal.
        if (focusState.hasFocus && !focused) memory?.focusedId = id
        if (focused && !focusState.hasFocus) {
            lostRecently = true
            coroutineScope.launch {
                withFrameNanos { }
                lostRecently = false
            }
        }
        focused = focusState.hasFocus
    }

    private var lostRecently = false

    override fun onDetach() {
        val memory = memory ?: return
        // A control that lost focus to another one (which then became the focused identity) is not what to restore.
        if (focused || (lostRecently && memory.focusedId == id)) memory.lost(id)
        memory.unregister(id, this)
        this.memory = null
        focused = false
        lostRecently = false
    }
}
