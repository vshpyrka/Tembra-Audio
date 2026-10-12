package audio.tembra.engine.perf

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The facade against a recording [PerfTraceSink] (contracts/tracing-facade.md): `section` returns the block's value
 * and ends the interval when the block throws; `animation` begins and ends an animation interval; `counter` records
 * the name and value; outside a measurement build every call is a no-op that never reaches the sink.
 */
class PerfTraceTest {

    private val sink = RecordingSink()

    init {
        PerfTrace.sinkForTest = sink
    }

    @AfterTest
    fun tearDown() {
        PerfTrace.sinkForTest = null
    }

    @Test
    fun sectionReturnsTheBlockValueAndBracketsIt() {
        val value = PerfTrace.section(PerfSection.TUNER_MODE_SWITCH) { 42 }
        assertEquals(42, value)
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(listOf("begin:ui:tuner.modeSwitch", "end:ui:tuner.modeSwitch"), sink.events)
        } else {
            assertEquals(emptyList(), sink.events, "no sink call outside a measurement build")
        }
    }

    @Test
    fun sectionEndsWhenTheBlockThrows() {
        assertFailsWith<IllegalStateException> {
            PerfTrace.section(PerfSection.TUNER_LOAD) { error("boom") }
        }
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(listOf("begin:ui:tuner.load", "end:ui:tuner.load"), sink.events)
        } else {
            assertEquals(emptyList(), sink.events)
        }
    }

    @Test
    fun animationBeginsAndEndsAnAnimationInterval() {
        PerfTrace.animation(PerfSection.METRONOME_TEMPO_CHANGE) { }
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(listOf("animationBegin:ui:metronome.tempoChange", "animationEnd:ui:metronome.tempoChange"), sink.events)
        } else {
            assertEquals(emptyList(), sink.events)
        }
    }

    @Test
    fun counterRecordsNameAndValue() {
        PerfTrace.counter(PerfCounter.AUDIO_XRUNS, 3)
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(listOf("counter:audio.xruns=3"), sink.events)
        } else {
            assertEquals(emptyList(), sink.events)
        }
    }

    @Test
    fun nestedSectionsOfDifferentNamesAreAllowed() {
        PerfTrace.section(PerfSection.METRONOME_LOAD) {
            PerfTrace.section(PerfSection.METRONOME_TEMPO_CHANGE) { }
        }
        if (PerfBuild.PERF_ENABLED) {
            assertEquals(4, sink.events.size)
            assertTrue(sink.events.first().startsWith("begin:") && sink.events.last().startsWith("end:"))
        }
    }

    private class RecordingSink : PerfTraceSink {
        val events = mutableListOf<String>()
        override fun begin(category: String, name: String) { events += "begin:$category:$name" }
        override fun end(category: String, name: String) { events += "end:$category:$name" }
        override fun animationBegin(category: String, name: String) { events += "animationBegin:$category:$name" }
        override fun animationEnd(category: String, name: String) { events += "animationEnd:$category:$name" }
        override fun counter(name: String, value: Long) { events += "counter:$name=$value" }
    }
}
