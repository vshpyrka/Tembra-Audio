package audio.tembra.ui.perf

import kotlin.test.Test
import kotlin.test.assertEquals

/** The dropped-frame counter (feature 012, contracts/perf-instrumentation.md; T060): a frame later than twice the display period counts once. */
class MeterFrameCounterTest {

    private val frame = 16_666_667L

    @Test
    fun aFrameLaterThanTwiceThePeriodCountsOnce() {
        val counter = MeterFrameCounter()
        counter.frame(0L)
        counter.frame(frame)
        counter.frame(2 * frame)
        assertEquals(0L, counter.dropped, "steady frames")
        counter.frame(2 * frame + 3 * frame)
        assertEquals(1L, counter.dropped, "a 50 ms gap at 60 Hz is one drop")
        counter.frame(5 * frame + frame)
        assertEquals(1L, counter.dropped, "the next steady frame adds nothing")
        counter.frame(6 * frame + 2 * frame)
        assertEquals(1L, counter.dropped, "exactly two periods is not late")
        counter.frame(8 * frame + 2 * frame + 1)
        assertEquals(2L, counter.dropped, "a hair past two periods is")
    }

    @Test
    fun thePeriodIsTheShortestIntervalSeenAndNeverUnderFourMilliseconds() {
        val counter = MeterFrameCounter()
        counter.frame(0L)
        counter.frame(100_000_000L)  // the first interval is long: nothing counts yet
        counter.frame(100_000_000L + 8_333_333L)  // 120 Hz: the period shrinks
        counter.frame(100_000_000L + 8_333_333L + 100_000_000L)  // now late against 8.3 ms
        assertEquals(1L, counter.dropped)
        val fast = MeterFrameCounter()
        fast.frame(0L)
        fast.frame(1_000_000L)  // two draws of one frame, 1 ms apart: not a period
        fast.frame(1_000_000L + 7_000_000L)
        assertEquals(0L, fast.dropped, "7 ms is under twice the 4 ms floor")
    }

    @Test
    fun aPauseEndsTheRunWithoutCountingTheGap() {
        val counter = MeterFrameCounter()
        counter.frame(0L)
        counter.frame(frame)
        counter.pause()
        counter.frame(10 * frame)
        counter.frame(11 * frame)
        assertEquals(0L, counter.dropped)
        counter.reset()
        assertEquals(0L, counter.dropped)
    }

    @Test
    fun aRepeatedDrawOfTheSameFrameIsIgnored() {
        val counter = MeterFrameCounter()
        counter.frame(0L)
        counter.frame(frame)
        counter.frame(frame)
        counter.frame(2 * frame)
        assertEquals(0L, counter.dropped)
    }
}
