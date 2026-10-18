package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fifty immutable snapshots with undo and redo (data-model.md section 1; spec FR-035). */
class EditHistoryTest {

    private fun named(n: Int): Project = ProjectFixtures.empty.copy(name = "v$n")

    @Test
    fun startsWithNothingToUndoOrRedo() {
        val history = EditHistory.start(named(0))
        assertEquals(named(0), history.present)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(history, history.undo())
        assertEquals(history, history.redo())
    }

    @Test
    fun pushUndoRedoMoveOneSnapshot() {
        val history = EditHistory.start(named(0)).push(named(1)).push(named(2))
        assertEquals(named(2), history.present)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        val once = history.undo()
        assertEquals(named(1), once.present)
        assertTrue(once.canRedo)
        val twice = once.undo()
        assertEquals(named(0), twice.present)
        assertFalse(twice.canUndo)
        assertEquals(named(1), twice.redo().present)
        assertEquals(named(2), twice.redo().redo().present)
    }

    @Test
    fun aPushAfterUndoClearsTheRedoBranch() {
        val history = EditHistory.start(named(0)).push(named(1)).undo().push(named(5))
        assertFalse(history.canRedo)
        assertEquals(named(5), history.present)
        assertEquals(named(0), history.undo().present)
    }

    @Test
    fun capacityDropsTheOldestSnapshot() {
        var history = EditHistory.start(named(0), capacity = 50)
        for (n in 1..60) history = history.push(named(n))
        assertEquals(50, history.past.size)
        assertEquals(named(10), history.past.first())
        var back = history
        repeat(50) { back = back.undo() }
        assertEquals(named(10), back.present)
        assertFalse(back.canUndo)
    }

    @Test
    fun snapshotsListEveryProjectTheHistoryStillHolds() {
        val history = EditHistory.start(named(0)).push(named(1)).push(named(2)).undo()
        assertEquals(listOf(named(0), named(1), named(2)), history.snapshots)
    }
}
