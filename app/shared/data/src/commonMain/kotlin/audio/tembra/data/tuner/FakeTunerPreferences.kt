package audio.tembra.data.tuner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory preferences for tests, beside the interface like `FakeAudioEngine` (research R10). */
class FakeTunerPreferences(initial: TunerPreferencesSnapshot = TunerPreferencesSnapshot()) : TunerPreferences {
    private val state = MutableStateFlow(initial)
    override val snapshot: Flow<TunerPreferencesSnapshot> = state

    override suspend fun update(transform: (TunerPreferencesSnapshot) -> TunerPreferencesSnapshot) {
        state.update(transform)
    }
}
