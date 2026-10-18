package audio.tembra.data.projects

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 500 ms debounce, 2 s cap, flush and coalescing on virtual time (contracts/project-storage.md, Autosave; spec FR-046). */
class AutosaveSchedulerTest {

    @Test
    fun savesHalfASecondAfterTheLastEdit() = runTest {
        var saves = 0
        val scheduler = AutosaveScheduler(backgroundScope, save = { saves++ })
        scheduler.start()
        scheduler.onEdit()
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, saves)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, saves)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, saves)
    }

    @Test
    fun aSecondEditMovesTheSaveButTheCapForcesItTwoSecondsAfterTheFirst() = runTest {
        var saves = 0
        val scheduler = AutosaveScheduler(backgroundScope, save = { saves++ })
        scheduler.start()
        scheduler.onEdit()
        advanceTimeBy(400)
        scheduler.onEdit()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(0, saves)
        scheduler.onEdit()
        advanceTimeBy(400)
        scheduler.onEdit()
        advanceTimeBy(400)
        scheduler.onEdit()
        advanceTimeBy(399)
        runCurrent()
        assertEquals(0, saves)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, saves)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, saves)
    }

    @Test
    fun flushSavesAtOnceAndOnlyWhenSomethingIsPending() = runTest {
        var saves = 0
        val scheduler = AutosaveScheduler(backgroundScope, save = { saves++ })
        scheduler.start()
        scheduler.flush()
        assertEquals(0, saves)
        scheduler.onEdit()
        scheduler.flush()
        assertEquals(1, saves)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, saves)
    }

    @Test
    fun anEditDuringASaveYieldsExactlyOneMoreSaveAfterIt() = runTest {
        var saves = 0
        val scheduler = AutosaveScheduler(backgroundScope, save = { delay(300); saves++ })
        scheduler.start()
        scheduler.onEdit()
        advanceTimeBy(500)
        runCurrent()
        assertTrue(scheduler.saving.value)
        advanceTimeBy(100)
        scheduler.onEdit()
        scheduler.onEdit()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, saves)
        assertFalse(scheduler.saving.value)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(scheduler.saving.value)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, saves)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, saves)
    }

    @Test
    fun flushWaitsForTheSaveInFlightAndSavesAgainOnlyIfEdited() = runTest {
        var saves = 0
        val scheduler = AutosaveScheduler(backgroundScope, save = { delay(300); saves++ })
        scheduler.start()
        scheduler.onEdit()
        advanceTimeBy(500)
        runCurrent()
        scheduler.flush()
        assertEquals(1, saves)
        scheduler.onEdit()
        scheduler.flush()
        assertEquals(2, saves)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, saves)
    }
}
