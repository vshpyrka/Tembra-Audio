package audio.tembra.data.tuner

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.core.tuner.TunerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The DataStore round trip on a real file (contracts/tuner-preferences.md). */
@OptIn(ExperimentalForeignApi::class)
class DataStoreTunerPreferencesTest {

    private fun freshDirectory(): String {
        val path = NSTemporaryDirectory().trimEnd('/') + "/prefs-test-${Random.nextLong()}"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }

    @Test
    fun defaultsThenWrittenValuesSurviveReopening() = runTest {
        val directory = freshDirectory()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val first = DataStoreTunerPreferences(TembraDataStore.preferences(directory, firstScope))
        assertEquals(TunerPreferencesSnapshot(), first.snapshot.first())
        first.update { it.copy(instrument = Instrument.Cello, presetId = "cello-bach-5", mode = TunerMode.ByEar, loopTone = false) }
        val written = first.snapshot.first()
        assertEquals(Instrument.Cello, written.instrument)
        assertEquals("cello-bach-5", written.presetId)
        assertEquals(TunerMode.ByEar, written.mode)
        assertEquals(false, written.loopTone)
        firstScope.coroutineContext.job.cancelAndJoin()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val second = DataStoreTunerPreferences(TembraDataStore.preferences(directory, secondScope))
        assertEquals(written, second.snapshot.first())
        secondScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun aCorruptedValueReadsAsTheDefault() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = TembraDataStore.preferences(freshDirectory(), scope)
        dataStore.edit { it[stringPreferencesKey(TunerPreferenceKeys.INSTRUMENT)] = "banjo" }
        val preferences = DataStoreTunerPreferences(dataStore)
        assertEquals(Instrument.Guitar, preferences.snapshot.first().instrument)
        scope.coroutineContext.job.cancelAndJoin()
    }

    // --- The noise gate (feature 006, contracts/tuner-preferences.md) ---

    @Test
    fun theNoiseGateSurvivesReopeningAndItsKeyGoesWhenItIsOff() = runTest {
        val directory = freshDirectory()
        val key = intPreferencesKey(TunerPreferenceKeys.NOISE_GATE_DB)
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val firstStore = TembraDataStore.preferences(directory, firstScope)
        val first = DataStoreTunerPreferences(firstStore)
        assertEquals(NoiseGate.Off, first.snapshot.first().noiseGate)
        first.update { it.copy(noiseGate = NoiseGate.ofLevelDb(-50)!!) }
        assertEquals(-50, first.snapshot.first().noiseGate.levelDb)
        assertEquals(-50, firstStore.data.first()[key])
        firstScope.coroutineContext.job.cancelAndJoin()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val secondStore = TembraDataStore.preferences(directory, secondScope)
        val second = DataStoreTunerPreferences(secondStore)
        assertEquals(-50, second.snapshot.first().noiseGate.levelDb)
        second.update { it.copy(noiseGate = NoiseGate.Off) }
        assertEquals(NoiseGate.Off, second.snapshot.first().noiseGate)
        assertFalse(secondStore.data.first().contains(key), "the gate's key must be removed when the gate is off")
        secondScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun aFileFromBeforeTheGateAndAValueOffTheScaleReadOff() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = TembraDataStore.preferences(freshDirectory(), scope)
        dataStore.edit { it[stringPreferencesKey(TunerPreferenceKeys.INSTRUMENT)] = "cello" }
        val preferences = DataStoreTunerPreferences(dataStore)
        assertEquals(Instrument.Cello, preferences.snapshot.first().instrument)
        assertEquals(NoiseGate.Off, preferences.snapshot.first().noiseGate)
        dataStore.edit { it[intPreferencesKey(TunerPreferenceKeys.NOISE_GATE_DB)] = 7 }
        assertEquals(NoiseGate.Off, preferences.snapshot.first().noiseGate)
        dataStore.edit { it[intPreferencesKey(TunerPreferenceKeys.NOISE_GATE_DB)] = -51 }
        assertEquals(-52, preferences.snapshot.first().noiseGate.levelDb)
        scope.coroutineContext.job.cancelAndJoin()
    }
}
