package audio.tembra.data.metronome

import audio.tembra.core.metronome.Tempo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The in-memory preferences double used by the ViewModel tests. */
class FakeMetronomePreferencesTest {

    @Test
    fun emitsTheInitialValueFirst() = runTest {
        val initial = MetronomePreferencesSnapshot().let { it.copy(settings = it.settings.copy(tempo = Tempo(70))) }
        assertEquals(initial, FakeMetronomePreferences(initial).snapshot.first())
    }

    @Test
    fun updateIsSeenByTheNextEmissionAndByNewCollectors() = runTest {
        val preferences = FakeMetronomePreferences()
        preferences.update { it.copy(settings = it.settings.copy(tempo = Tempo(90))) }
        assertEquals(Tempo(90), preferences.snapshot.first().settings.tempo)
        assertEquals(Tempo(90), preferences.snapshot.first().settings.tempo)
    }
}
