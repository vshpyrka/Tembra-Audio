package audio.tembra.core.project

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Undo and redo as a history of immutable snapshots (Constitution V; spec FR-035): [past] holds at most [capacity]
 * earlier projects, [present] the current one, [future] the undone ones. Itself immutable so a session can keep it
 * in a `StateFlow` and `update` it; cleared (replaced) when another project opens; never persisted.
 */
data class EditHistory(
    val present: Project,
    val past: PersistentList<Project> = persistentListOf(),
    val future: PersistentList<Project> = persistentListOf(),
    val capacity: Int = DEFAULT_CAPACITY,
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    val canRedo: Boolean get() = future.isNotEmpty()

    /** Every project the history still holds, oldest first: what asset retirement looks at. */
    val snapshots: List<Project> get() = past + present + future

    /** Records [next] as the present; the oldest past snapshot is dropped beyond [capacity] and the redo branch is cleared. */
    fun push(next: Project): EditHistory {
        val kept = if (past.size >= capacity) past.removeAt(0) else past
        return copy(present = next, past = kept.add(present), future = persistentListOf())
    }

    /** One step back; unchanged when there is nothing to undo. */
    fun undo(): EditHistory {
        if (!canUndo) return this
        return copy(present = past.last(), past = past.removeAt(past.size - 1), future = future.add(0, present))
    }

    /** One step forward; unchanged when there is nothing to redo. */
    fun redo(): EditHistory {
        if (!canRedo) return this
        return copy(present = future.first(), past = past.add(present), future = future.removeAt(0))
    }

    companion object {
        const val DEFAULT_CAPACITY = 50

        fun start(project: Project, capacity: Int = DEFAULT_CAPACITY): EditHistory = EditHistory(present = project, capacity = capacity)
    }
}
