package audio.tembra.ui.metronome

import audio.tembra.core.metronome.SyncopationReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The debug launch argument's value (data-model.md §10, research R14): the three reading ids, anything else ignored. */
class MetronomeDebugRequestTest {

    @Test
    fun theThreeReadingIdsParse() {
        assertEquals(MetronomeDebugRequest(SyncopationReading.OffBeatTicks), MetronomeDebugRequest.parse("off-beat-ticks"))
        assertEquals(MetronomeDebugRequest(SyncopationReading.EvenBeats), MetronomeDebugRequest.parse("even-beats"))
        assertEquals(MetronomeDebugRequest(SyncopationReading.AllButOne), MetronomeDebugRequest.parse("all-but-one"))
    }

    @Test
    fun anythingElseIsIgnored() {
        assertNull(MetronomeDebugRequest.parse("x"))
        assertNull(MetronomeDebugRequest.parse(null))
        assertNull(MetronomeDebugRequest.parse(""))
        assertNull(MetronomeDebugRequest.parse("OFF-BEAT-TICKS"))
    }
}
