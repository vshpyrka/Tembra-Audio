package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real engine, Oboe stream and JNI read path keep 120 BPM on time (spec SC-003, SC-007; T027). Each tick's audible
 * instant is estimated as `now + audibleInNanos` on the monotonic clock, so the 20 ms poll granularity does not enter
 * the spacing.
 */
class MetronomeTimingEndToEndTest {
    private val engine = NativeAudioEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @AfterTest
    fun tearDown() = engine.stopMetronome()

    @Test
    fun ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly() {
        val levels = listOf(ClickLevel.Accent, ClickLevel.Click, ClickLevel.Click, ClickLevel.Click)
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, levels)))
        assertEquals(AudioResult.Ok, engine.startMetronome())
        val started = System.nanoTime()
        val tick = TickSnapshot()

        // Start latency (SC-007): the first tick is reported within 100 ms of startMetronome() returning.
        var firstTickAt = 0L
        while (firstTickAt == 0L) {
            engine.readTick(tick)
            val now = System.nanoTime()
            if (tick.running && tick.tickIndex >= 1L) firstTickAt = now
            if (now - started > 1_000_000_000L) fail("no tick within one second of the start")
            Thread.sleep(1)
        }
        val startLatencyMs = (firstTickAt - started) / 1e6
        println("MetronomeTimingEndToEndTest: start latency $startLatencyMs ms")
        assertTrue(startLatencyMs <= 100.0, "start latency $startLatencyMs ms")

        // As the ViewModel's poll does shortly after a start: ask the device for its latency again (0 = ask the device).
        Thread.sleep(200)
        engine.reportOutputLatencyNanos(0)
        val measureFrom = System.nanoTime()

        val indices = mutableListOf<Long>()
        val audibleAt = mutableListOf<Long>()
        while (System.nanoTime() - measureFrom < 5_000_000_000L) {
            val before = System.nanoTime()
            engine.readTick(tick)
            val now = (before + System.nanoTime()) / 2
            assertTrue(tick.running, "the metronome stopped running")
            if (tick.audibleInNanos <= 0L && tick.tickIndex != indices.lastOrNull()) {
                indices += tick.tickIndex
                audibleAt += now + tick.audibleInNanos
            }
            Thread.sleep(20)
        }

        // Every read above came after the latency was re-read, and readTick applies the latency at read time.
        val spacingsMs = audibleAt.zipWithNext { a, b -> (b - a) / 1e6 }
        println("MetronomeTimingEndToEndTest: ${audibleAt.size} crossings, spacings $spacingsMs ms")
        assertTrue(audibleAt.size >= 9, "only ${audibleAt.size} crossings: $indices")
        indices.zipWithNext { a, b -> assertEquals(a + 1, b, "tick indices $indices") }
        for (spacing in spacingsMs) assertTrue(abs(spacing - 500.0) <= 1.0, "spacing $spacing ms in $spacingsMs")

        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
    }
}
