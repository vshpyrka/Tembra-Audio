package audio.tembra.data.metronome

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.CustomSoundRef
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.data.tuner.TembraDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The metronome DataStore round trip on a real file (contracts/metronome-preferences.md). */
@OptIn(ExperimentalForeignApi::class)
class DataStoreMetronomePreferencesTest {

    private fun freshDirectory(): String {
        val path = NSTemporaryDirectory().trimEnd('/') + "/metronome-prefs-test-${Random.nextLong()}"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }

    @Test
    fun defaultsThenWrittenValuesSurviveReopening() = runTest {
        val directory = freshDirectory()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val first = DataStoreMetronomePreferences(TembraDataStore.preferences(directory, firstScope))
        assertEquals(MetronomePreferencesSnapshot(), first.snapshot.first())
        first.update {
            it.copy(
                settings = MetronomeSettings(
                    tempo = Tempo(75),
                    signature = TimeSignature.SixEight,
                    subdivision = Subdivision.Triplet,
                    pattern = MetronomeStyle.Downbeat.pattern(TimeSignature.SixEight),
                    style = MetronomeStyle.Downbeat,
                    sound = ClickSound.Custom("clap"),
                    customSound = CustomSoundRef("clap", "/docs/metronome/custom.wav"),
                ),
            )
        }
        val written = first.snapshot.first()
        assertEquals(Tempo(75), written.settings.tempo)
        assertEquals(ClickSound.Custom("clap"), written.settings.sound)
        firstScope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val second = DataStoreMetronomePreferences(TembraDataStore.preferences(directory, secondScope))
        assertEquals(written, second.snapshot.first())
        secondScope.cancel()
    }

    @Test
    fun aRemovedCustomSoundIsForgotten() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val preferences = DataStoreMetronomePreferences(TembraDataStore.preferences(freshDirectory(), scope))
        preferences.update { it.copy(settings = it.settings.copy(customSound = CustomSoundRef("clap", "/p"))) }
        preferences.update { it.copy(settings = it.settings.copy(customSound = null, sound = ClickSound.BuiltIn(BuiltInClickSound.Cowbell))) }
        assertEquals(null, preferences.snapshot.first().settings.customSound)
        scope.cancel()
    }

    @Test
    fun aCorruptedTempoReadsAsTheDefault() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = TembraDataStore.preferences(freshDirectory(), scope)
        dataStore.edit { it[stringPreferencesKey(MetronomePreferenceKeys.TEMPO)] = "x" }
        assertEquals(Tempo(120), DataStoreMetronomePreferences(dataStore).snapshot.first().settings.tempo)
        scope.cancel()
    }
}
