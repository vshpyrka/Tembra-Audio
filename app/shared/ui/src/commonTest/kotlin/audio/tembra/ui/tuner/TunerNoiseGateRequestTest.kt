package audio.tembra.ui.tuner

import audio.tembra.core.tuner.NoiseGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The debug launch argument that sets the noise gate for one launch (feature 006, data-model.md §8). */
class TunerNoiseGateRequestTest {

    @Test
    fun offIsTheGateSwitchedOff() {
        assertEquals(NoiseGate.Off, TunerNoiseGateRequest.parse("off")?.gate)
    }

    @Test
    fun aWholeDecibelNumberOnTheScaleIsThatLevel() {
        assertEquals(-50, TunerNoiseGateRequest.parse("-50")?.gate?.levelDb)
        assertEquals(-78, TunerNoiseGateRequest.parse("-78")?.gate?.levelDb)
        assertEquals(-20, TunerNoiseGateRequest.parse("-20")?.gate?.levelDb)
    }

    @Test
    fun aNumberBetweenTwoStepsGoesToTheQuieterOne() {
        assertEquals(-52, TunerNoiseGateRequest.parse("-51")?.gate?.levelDb)
    }

    @Test
    fun anythingElseIsIgnored() {
        assertNull(TunerNoiseGateRequest.parse(null))
        for (bad in listOf("", "loud", "-19", "5", "-80", "-50.5", " -50", "OFF", "-50dB")) {
            assertNull(TunerNoiseGateRequest.parse(bad), "\"$bad\" should be ignored")
        }
    }
}
