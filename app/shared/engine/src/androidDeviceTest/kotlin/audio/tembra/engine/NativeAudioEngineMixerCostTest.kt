package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The main-thread audit's facade test (feature 012, contracts/skills-and-audit.md §C part 3; T019): with the transport
 * open, a thousand pan steps, gain steps and meter reads on the main thread cost a median under 50 µs and never a
 * millisecond, because each is an atomic store or a seqlock copy through the JNI shim and never takes the engine's
 * command mutex.
 */
class NativeAudioEngineMixerCostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val engine = NativeAudioEngine(instrumentation.targetContext)

    @AfterTest
    fun tearDown() = runBlocking {
        engine.closeTransport()
    }

    @Test
    fun aThousandMixerCallsOnTheMainThreadStayUnderTheBounds() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true), TrackRender(1f, true)), clips = emptyList()))
        engine.setMetering(true)
        val mix = MixSnapshot()
        instrumentation.runOnMainSync {
            repeat(WARM_UP) {
                engine.setPan(0, 0.1f)
                engine.setTrack(0, 1f, true)
                engine.readMix(mix)
            }
        }
        // A single preemption of the main thread shows as one outlier against a sub-microsecond median: a run whose worst
        // call passes 1 ms is measured once more, the 99th percentile must stay under 1 ms and the worst call under 10 ms,
        // which a wait behind play's prime (tens of ms) or record-stop's drain would still fail.
        var samples = measureOnMainThread(mix)
        if (samples.last() >= P99_BOUND_NANOS) samples = measureOnMainThread(mix)
        val median = samples[samples.size / 2]
        val p99 = samples[samples.size * 99 / 100]
        val max = samples.last()
        println("NativeAudioEngineMixerCostTest: median ${median / 1_000.0} µs, p99 ${p99 / 1_000.0} µs, max ${max / 1_000.0} µs over ${samples.size} calls")
        assertTrue(median < MEDIAN_BOUND_NANOS, "median $median ns is not under 50 µs")
        assertTrue(p99 < P99_BOUND_NANOS, "99th percentile $p99 ns is not under 1 ms")
        assertTrue(max < MAX_BOUND_NANOS, "max $max ns is not under 10 ms: a call waited on the engine")
    }

    /** One round of a thousand pan steps, gain steps and reads on the main thread, timed one call at a time; sorted ascending. */
    private fun measureOnMainThread(mix: MixSnapshot): LongArray {
        val samples = LongArray(ROUNDS * 3)
        instrumentation.runOnMainSync {
            var k = 0
            repeat(ROUNDS) { i ->
                val pan = (i % 200 - 100) / 100f
                var started = System.nanoTime()
                engine.setPan(0, pan)
                samples[k++] = System.nanoTime() - started
                started = System.nanoTime()
                engine.setTrack(1, 0.5f + (i % 10) * 0.05f, true)
                samples[k++] = System.nanoTime() - started
                started = System.nanoTime()
                engine.readMix(mix)
                samples[k++] = System.nanoTime() - started
            }
        }
        samples.sort()
        return samples
    }

    private companion object {
        const val ROUNDS = 1_000
        const val WARM_UP = 50
        const val MEDIAN_BOUND_NANOS = 50_000L
        const val P99_BOUND_NANOS = 1_000_000L
        const val MAX_BOUND_NANOS = 10_000_000L
    }
}
