package audio.tembra.ui.designsystem

/**
 * One destination's memory of the control that had keyboard focus, by its semantic identity (feature 011, spec FR-007;
 * contracts/window-behaviour.md). A window crossing a layout class rebuilds controls in another form; the control
 * that was focused reports itself lost when it leaves, and the destination's root ([focusRestoringRoot]) gives focus
 * back, a frame later, to the control with the same identity in the new form, or takes it itself when there is none.
 * Created once per destination's content, above every layout branch, so it outlives a rebuilt root too.
 */
internal class FocusMemory {
    private val controls = mutableMapOf<String, MutableList<FocusIdentityNode>>()
    internal var root: FocusRestoringRootNode? = null

    /** The identity that took focus last: a control that merely lost focus to another one is not restored. */
    internal var focusedId: String? = null

    /** The identity whose control left while it had focus; cleared once focus has been placed again. */
    internal var pending: String? = null
        private set

    internal fun register(id: String, node: FocusIdentityNode) {
        controls.getOrPut(id) { mutableListOf() } += node
    }

    internal fun unregister(id: String, node: FocusIdentityNode) {
        controls[id]?.let { nodes ->
            nodes -= node
            if (nodes.isEmpty()) controls -= id
        }
    }

    /** The newest attached control with [id], if any. */
    internal fun controlFor(id: String): FocusIdentityNode? = controls[id]?.lastOrNull { it.isAttached }

    internal fun lost(id: String) {
        pending = id
        root?.scheduleRestore()
    }

    internal fun takePending(): String? = pending.also { pending = null }
}
