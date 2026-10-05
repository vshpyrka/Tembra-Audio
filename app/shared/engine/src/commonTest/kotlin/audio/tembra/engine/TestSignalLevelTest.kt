package audio.tembra.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Levels in the noise gate's measure become test-signal amplitudes (feature 006 data-model.md §7). */
class TestSignalLevelTest {

    @Test
    fun theDefaultMixHasACrestOfTwoPointOneEight() {
        val crest = TestSignalLevel.crest()
        assertTrue(abs(crest - 2.18) < 0.01, "crest $crest")
    }

    @Test
    fun minusThirtyDecibelsIsAPeakOfAboutSevenHundredths() {
        val amplitude = TestSignalLevel.amplitudeFor(-30f)
        assertTrue(abs(amplitude - 0.0690f) < 0.0005f, "amplitude $amplitude")
    }

    @Test
    fun twentyDecibelsLessIsATenthOfTheAmplitude() {
        val loud = TestSignalLevel.amplitudeFor(-30f)
        val quiet = TestSignalLevel.amplitudeFor(-50f)
        assertTrue(abs(quiet * 10f - loud) < 0.0001f, "loud $loud quiet $quiet")
    }

    @Test
    fun aSingleSineHasTheCrestOfASine() {
        val crest = TestSignalLevel.crest(harmonics = 1, fundamentalGain = 1f)
        assertTrue(abs(crest - 1.4142) < 0.001, "crest $crest")
    }
}
