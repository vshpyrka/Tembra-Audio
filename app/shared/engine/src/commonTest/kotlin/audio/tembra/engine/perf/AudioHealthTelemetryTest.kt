package audio.tembra.engine.perf

import audio.tembra.engine.FakeAudioEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The audio-health plumbing without the real engine (spec SC-010): the fake engine scripts an [AudioHealth], the
 * facade returns it, and [AudioHealthPublisher.publish] turns it into exactly fifteen counters through [PerfTrace]. The
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
    fun thePublisherEmitsFifteenCountersWithTheScriptedValues() {
        val engine = FakeAudioEngine()
        engine.scriptAudioHealth(
            AudioHealth(
                callbacks = 1200, lastMicros = 850, maxMicros = 1900, overBudget = 2, xruns = 3, realtime = true, avgMicros = 410, starvation = 1, clipsActive = 5,
                inputOverruns = 1, recordBacklog = 4096, inputXruns = 2, masterClips = 7,
            ),
        )
        // Armed and monitoring: the alignment (960 frames at 48 kHz = 20 ms) and the monitoring delay (7 + 13 ms) come from the snapshot.
        engine.nextCapture.armed = true
        engine.nextCapture.monitoring = true
        engine.nextCapture.sampleRate = 48000
        engine.nextCapture.alignmentFrames = 960
        engine.nextCapture.inputLatencyNanos = 7_000_000
        engine.nextCapture.outputLatencyNanos = 13_000_000
        // Feature 012: the master clamp count rides in the health struct; the dropped meter frames are the UI's count.
        val published = AudioHealthPublisher.publish(engine, PerfTrace, meterFramesDropped = 3)
        assertEquals(true, published)
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(
                listOf(
                    "audio.callbacks=1200", "audio.lastUs=850", "audio.maxUs=1900", "audio.overBudget=2", "audio.xruns=3", "audio.realtime=1",
                    "audio.avgUs=410", "audio.starvation=1", "audio.clipsActive=5",
                    "audio.inputOverruns=1", "audio.recordBacklog=4096", "audio.takeAlignmentMs=20", "audio.monitorDelayMs=20",
                    "audio.masterClips=7", "audio.meterFramesDropped=3",
                ),
                sink.counters,
            )
        } else {
            assertEquals(emptyList(), sink.counters, "inert outside a measurement build")
        }
    }

    @Test
    fun theCaptureAndMixerCountersReadZeroWhileNothingIsArmedOrOpen() {
        val engine = FakeAudioEngine()
        engine.scriptAudioHealth(AudioHealth(callbacks = 1, lastMicros = 1, maxMicros = 1, overBudget = 0, xruns = 0, realtime = true))
        assertEquals(true, AudioHealthPublisher.publish(engine, PerfTrace))
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(
                listOf("audio.inputOverruns=0", "audio.recordBacklog=0", "audio.takeAlignmentMs=0", "audio.monitorDelayMs=0", "audio.masterClips=0", "audio.meterFramesDropped=0"),
                sink.counters.takeLast(6),
            )
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
