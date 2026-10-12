package audio.tembra.engine.perf

import audio.tembra.engine.FakeAudioEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The audio-health plumbing without the real engine (spec SC-010): the fake engine scripts an [AudioHealth], the
 * facade returns it, and [AudioHealthPublisher.publish] turns it into exactly six counters through [PerfTrace]. The
 * publisher is a plain function: the launch site is the app's one-second ticker, not this module.
 */
class AudioHealthTelemetryTest {

    private val sink = RecordingSink()

    init {
        PerfTrace.sinkForTest = sink
    }

    @AfterTest
    fun tearDown() {
        PerfTrace.sinkForTest = null
    }

    @Test
    fun theFakeEngineReportsNoHealthUntilScripted() {
        val engine = FakeAudioEngine()
        assertNull(engine.audioHealth())
        val health = AudioHealth(callbacks = 1200, lastMicros = 850, maxMicros = 1900, overBudget = 0, xruns = 0, realtime = true)
        engine.scriptAudioHealth(health)
        assertEquals(health, engine.audioHealth())
    }

    @Test
    fun thePublisherEmitsSixCountersWithTheScriptedValues() {
        val engine = FakeAudioEngine()
        engine.scriptAudioHealth(AudioHealth(callbacks = 1200, lastMicros = 850, maxMicros = 1900, overBudget = 2, xruns = 3, realtime = true))
        val published = AudioHealthPublisher.publish(engine, PerfTrace)
        assertEquals(true, published)
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(
                listOf("audio.callbacks=1200", "audio.lastUs=850", "audio.maxUs=1900", "audio.overBudget=2", "audio.xruns=3", "audio.realtime=1"),
                sink.counters,
            )
        } else {
            assertEquals(emptyList(), sink.counters, "inert outside a measurement build")
        }
    }

    @Test
    fun nothingIsPublishedWithoutAnOpenStream() {
        val engine = FakeAudioEngine()
        assertEquals(false, AudioHealthPublisher.publish(engine, PerfTrace))
        assertEquals(emptyList(), sink.counters)
    }

    private class RecordingSink : PerfTraceSink {
        val counters = mutableListOf<String>()
        override fun begin(category: String, name: String) = Unit
        override fun end(category: String, name: String) = Unit
        override fun animationBegin(category: String, name: String) = Unit
        override fun animationEnd(category: String, name: String) = Unit
        override fun counter(name: String, value: Long) { counters += "$name=$value" }
    }
}
