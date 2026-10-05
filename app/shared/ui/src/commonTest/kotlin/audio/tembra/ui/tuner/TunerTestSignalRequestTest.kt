package audio.tembra.ui.tuner

import audio.tembra.core.tuner.Note
import audio.tembra.engine.TestSignal
import audio.tembra.engine.TestSignalLevel
import audio.tembra.engine.TestSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The debug launch argument: catalogue ids and `NOTE:CENTS` (data-model.md §9, spec FR-032). */
class TunerTestSignalRequestTest {

    @Test
    fun catalogueIdsResolveToTheirSignal() {
        assertNull(TunerTestSignalRequest.parse(null))
        assertEquals(TestSignals.byId("e2-flat-20")!!.signal, TunerTestSignalRequest.parse("e2-flat-20"))
        assertEquals(TestSignals.byId("out-of-range-2khz")!!.signal, TunerTestSignalRequest.parse("out-of-range-2khz"))
    }

    @Test
    fun noteAndCentsAreParsed() {
        assertEquals(TestSignal.Pitched(Note.parse("E2"), -20.0), TunerTestSignalRequest.parse("E2:-20"))
        assertEquals(TestSignal.Pitched(Note.parse("F#3"), 5.0), TunerTestSignalRequest.parse("F#3:+5"))
        assertEquals(TestSignal.Pitched(Note.parse("A1"), 0.0), TunerTestSignalRequest.parse("A1:0"))
        assertEquals(TestSignal.Pitched(Note.parse("D3"), -5.5), TunerTestSignalRequest.parse("D3:-5.5"))
    }

    /** Feature 006: the four signals at a set level, by id. */
    @Test
    fun theLevelledCatalogueIdsResolve() {
        for (id in listOf("e2-70db", "e2-55db", "e2-45db", "e2-30db")) {
            assertEquals(TestSignals.byId(id)!!.signal, TunerTestSignalRequest.parse(id), id)
        }
    }

    /** Feature 006: `NOTE:CENTS@LEVEL` gives the note the amplitude whose mix has that level. */
    @Test
    fun aLevelAfterTheCentsSetsTheAmplitude() {
        assertEquals(
            TestSignal.Pitched(Note.parse("E2"), 0.0, amplitude = TestSignalLevel.amplitudeFor(-55f)),
            TunerTestSignalRequest.parse("E2:0@-55"),
        )
        assertEquals(
            TestSignal.Pitched(Note.parse("G3"), -5.0, amplitude = TestSignalLevel.amplitudeFor(-32.5f)),
            TunerTestSignalRequest.parse("G3:-5@-32.5"),
        )
        assertEquals(TestSignal.Pitched(Note.parse("E2"), 0.0, amplitude = TestSignalLevel.amplitudeFor(0f)), TunerTestSignalRequest.parse("E2:0@0"))
        assertEquals(TestSignal.Pitched(Note.parse("E2"), 0.0, amplitude = TestSignalLevel.amplitudeFor(-120f)), TunerTestSignalRequest.parse("E2:0@-120"))
        for (bad in listOf("E2:0@1", "E2:0@x", "E2:0@", "E2:0@-121", "E2@-55", "e2-55db@-40")) {
            assertNull(TunerTestSignalRequest.parse(bad), "\"$bad\" should be ignored")
        }
    }

    @Test
    fun anythingElseIsIgnored() {
        for (bad in listOf("banjo", "E2", "E2:abc", "", "Eb2:0", "E2:", ":5")) {
            assertNull(TunerTestSignalRequest.parse(bad), "\"$bad\" should be ignored")
        }
    }
}
