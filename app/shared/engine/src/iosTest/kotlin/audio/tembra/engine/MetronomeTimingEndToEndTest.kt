package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import platform.Foundation.NSThread
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.TimeSource

/**
 * The real engine, RemoteIO unit and cinterop read path keep 120 BPM on time (spec SC-003, SC-007; T027). Each tick's
 * audible instant is estimated as `now + audibleInNanos` on the monotonic clock, so the 20 ms poll granularity does not
 * enter the spacing. The bare simulator test process has no output unit (feature 003): there the start reports the
 * device error and the measurement is skipped, as the tone round trip is.
 */
class MetronomeTimingEndToEndTest {
    private val engine = NativeAudioEngine()
    private val origin = TimeSource.Monotonic.markNow()

    private fun nowNanos(): Long = origin.elapsedNow().inWholeNanoseconds

    @AfterTest
    fun tearDown() = engine.stopMetronome()

    @Test
    fun ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly() {
        val levels = listOf(ClickLevel.Accent, ClickLevel.Click, ClickLevel.Click, ClickLevel.Click)
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, levels)))
        when (val result = engine.startMetronome()) {
            AudioResult.Ok -> measure()
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                assertEquals(AudioState.Idle, engine.audioState)
                println("MetronomeTimingEndToEndTest: no output unit in this simulator process; timing skipped")
            }
        }
        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    private fun measure() {
        val started = nowNanos()
        val tick = TickSnapshot()

        // Start latency (SC-007): the first tick is reported within 100 ms of startMetronome() returning.
        var firstTickAt = 0L
        while (firstTickAt == 0L) {
            engine.readTick(tick)
            val now = nowNanos()
            if (tick.running && tick.tickIndex >= 1L) firstTickAt = now
            if (now - started > 1_000_000_000L) fail("no tick within one second of the start")
            NSThread.sleepForTimeInterval(0.001)
        }
        val startLatencyMs = (firstTickAt - started) / 1e6
        println("MetronomeTimingEndToEndTest: start latency $startLatencyMs ms")
        assertTrue(startLatencyMs <= 100.0, "start latency $startLatencyMs ms")

        val measureFrom = nowNanos()
        val indices = mutableListOf<Long>()
        val audibleAt = mutableListOf<Long>()
        while (nowNanos() - measureFrom < 5_000_000_000L) {
            val before = nowNanos()
            engine.readTick(tick)
            val now = (before + nowNanos()) / 2
            assertTrue(tick.running, "the metronome stopped running")
            if (tick.audibleInNanos <= 0L && tick.tickIndex != indices.lastOrNull()) {
                indices += tick.tickIndex
                audibleAt += now + tick.audibleInNanos
            }
            NSThread.sleepForTimeInterval(0.020)
        }

        val spacingsMs = audibleAt.zipWithNext { a, b -> (b - a) / 1e6 }
        println("MetronomeTimingEndToEndTest: ${audibleAt.size} crossings, spacings $spacingsMs ms")
        assertTrue(audibleAt.size >= 9, "only ${audibleAt.size} crossings: $indices")
        indices.zipWithNext { a, b -> assertEquals(a + 1, b, "tick indices $indices") }
        assertOnTime(indices, audibleAt, spacingsMs)
    }
}

/**
 * The ticks as the beat light sees them keep 120 BPM: a line fitted through (tick index, audible instant) has a period
 * of 500 ms ± 1 ms (the unit runs at its reported rate and nothing drifts), and no estimate is more than 15 ms off that
 * line. Where each click lands in the audio is the renderer's, frame-exact (SC-001; host test "ten minutes of ticks land
 * on round(i × framesPerTick) without drift"); an estimate also carries the jitter of the callback that rendered it,
 * which a ±1 ms limit per spacing mistook for drift on Android phones (the Android twin, 2026-10-16). 15 ms is half of
 * the 30 ms SC-006 allows between the lit cell and the click. The period bound is there
 * for a unit run at the wrong rate (44.1 for 48 kHz moves it by 44 ms).
 */
internal fun assertOnTime(indices: List<Long>, audibleAt: List<Long>, spacingsMs: List<Double>) {
    val x = indices.map { (it - indices.first()).toDouble() }
    val y = audibleAt.map { (it - audibleAt.first()) / 1e6 }
    val meanX = x.average()
    val meanY = y.average()
    val periodMs = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) } / x.sumOf { (it - meanX) * (it - meanX) }
    val deviationMs = x.indices.maxOf { abs(y[it] - (meanY + periodMs * (x[it] - meanX))) }
    println("MetronomeTimingEndToEndTest: fitted period $periodMs ms, largest deviation $deviationMs ms")
    assertTrue(abs(periodMs - 500.0) <= 1.0, "fitted period $periodMs ms; spacings $spacingsMs")
    assertTrue(deviationMs <= 15.0, "a tick $deviationMs ms off the fitted line; spacings $spacingsMs")
}
