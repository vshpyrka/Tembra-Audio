package audio.tembra.data.tuner

import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The in-memory preferences double used by the ViewModel tests. */
class FakeTunerPreferencesTest {

    @Test
    fun emitsTheInitialValueFirst() = runTest {
        val preferences = FakeTunerPreferences(TunerPreferencesSnapshot(instrument = Instrument.Cello))
        assertEquals(Instrument.Cello, preferences.snapshot.first().instrument)
    }

    @Test
    fun updateIsSeenByTheNextEmissionAndByNewCollectors() = runTest {
        val preferences = FakeTunerPreferences()
        preferences.update { it.copy(loopTone = false) }
        assertEquals(false, preferences.snapshot.first().loopTone)
        assertEquals(false, preferences.snapshot.first().loopTone)
        preferences.update { it.copy(presetId = "guitar6-drop-d") }
        assertEquals("guitar6-drop-d", preferences.snapshot.first().presetId)
    }

    @Test
    fun anUpdateOfTheNoiseGateIsEmitted() = runTest {
        val preferences = FakeTunerPreferences()
        assertEquals(NoiseGate.Off, preferences.snapshot.first().noiseGate)
        preferences.update { it.copy(noiseGate = NoiseGate.ofLevelDb(-50)!!) }
        assertEquals(-50, preferences.snapshot.first().noiseGate.levelDb)
    }
}
