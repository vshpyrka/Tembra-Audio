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
        for (spacing in spacingsMs) assertTrue(abs(spacing - 500.0) <= 1.0, "spacing $spacing ms in $spacingsMs")
    }
}
