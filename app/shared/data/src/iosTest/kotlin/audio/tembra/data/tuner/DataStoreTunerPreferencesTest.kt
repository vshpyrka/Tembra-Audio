package audio.tembra.data.tuner

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

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
        firstScope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val second = DataStoreTunerPreferences(TembraDataStore.preferences(directory, secondScope))
        assertEquals(written, second.snapshot.first())
        secondScope.cancel()
    }

    @Test
    fun aCorruptedValueReadsAsTheDefault() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = TembraDataStore.preferences(freshDirectory(), scope)
        dataStore.edit { it[stringPreferencesKey(TunerPreferenceKeys.INSTRUMENT)] = "banjo" }
        val preferences = DataStoreTunerPreferences(dataStore)
        assertEquals(Instrument.Guitar, preferences.snapshot.first().instrument)
        scope.cancel()
    }
}
