package audio.tembra.data.metronome

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory preferences for tests, beside the interface. */
class FakeMetronomePreferences(initial: MetronomePreferencesSnapshot = MetronomePreferencesSnapshot()) : MetronomePreferences {
    private val state = MutableStateFlow(initial)
    override val snapshot: Flow<MetronomePreferencesSnapshot> = state

    override suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot) {
        state.update(transform)
    }
}
