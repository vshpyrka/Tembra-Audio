package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSThread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The main-thread audit's facade test on the simulator (feature 012, contracts/skills-and-audit.md §C part 3; T019): a
 * thousand pan steps, gain steps and meter reads cost a median under 50 µs and never a millisecond, because each is an
 * atomic store or a seqlock copy through cinterop and never takes the engine's command mutex. The Kotlin/Native test
 * runner drives this on the main thread; in the bare simulator process the transport cannot open and the calls are
 * refused by state, which is the same bounded path, so the bounds hold either way.
 */
@OptIn(ExperimentalForeignApi::class)
class NativeAudioEngineMixerCostTest {
    private val engine = NativeAudioEngine()

    @AfterTest
    fun tearDown() = runBlocking {
        engine.closeTransport()
    }

    @Test
    fun aThousandMixerCallsStayUnderTheBounds() = runBlocking {
        val opened = engine.openTransport()
        if (opened == TransportOpenResult.Opened) {
            engine.setTempo(Tempo(120), TimeSignature.FourFour)
            engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true), TrackRender(1f, true)), clips = emptyList()))
            engine.setMetering(true)
        } else {
            println("NativeAudioEngineMixerCostTest: no output unit in this simulator process ($opened); the refused path is timed (main thread: ${NSThread.isMainThread})")
        }
        val mix = MixSnapshot()
        val samples = LongArray(ROUNDS * 3)
        repeat(WARM_UP) {
            engine.setPan(0, 0.1f)
            engine.setTrack(0, 1f, true)
            engine.readMix(mix)
        }
        var k = 0
        val clock = TimeSource.Monotonic
        repeat(ROUNDS) { i ->
            val pan = (i % 200 - 100) / 100f
            var started = clock.markNow()
            engine.setPan(0, pan)
            samples[k++] = started.elapsedNow().inWholeNanoseconds
            started = clock.markNow()
            engine.setTrack(1, 0.5f + (i % 10) * 0.05f, true)
            samples[k++] = started.elapsedNow().inWholeNanoseconds
            started = clock.markNow()
            engine.readMix(mix)
            samples[k++] = started.elapsedNow().inWholeNanoseconds
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
