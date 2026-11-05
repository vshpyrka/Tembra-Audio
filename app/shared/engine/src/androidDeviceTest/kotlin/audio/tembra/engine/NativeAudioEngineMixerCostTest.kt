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
        val samples = LongArray(ROUNDS * 3)
        instrumentation.runOnMainSync {
            repeat(WARM_UP) {
                engine.setPan(0, 0.1f)
                engine.setTrack(0, 1f, true)
                engine.readMix(mix)
            }
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
        val median = samples[samples.size / 2]
        val max = samples.last()
        println("NativeAudioEngineMixerCostTest: median ${median / 1_000.0} µs, max ${max / 1_000.0} µs over ${samples.size} calls")
        assertTrue(median < MEDIAN_BOUND_NANOS, "median $median ns is not under 50 µs")
        assertTrue(max < MAX_BOUND_NANOS, "max $max ns is not under 1 ms")
    }

    private companion object {
        const val ROUNDS = 1_000
        const val WARM_UP = 50
        const val MEDIAN_BOUND_NANOS = 50_000L
        const val MAX_BOUND_NANOS = 1_000_000L
    }
}
