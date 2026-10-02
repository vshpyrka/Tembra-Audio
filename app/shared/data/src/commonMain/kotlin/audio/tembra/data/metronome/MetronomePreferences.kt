package audio.tembra.data.metronome

import kotlinx.coroutines.flow.Flow

/** The metronome's remembered state (contracts/metronome-preferences.md). */
interface MetronomePreferences {
    /** The current value first, then every change. */
    val snapshot: Flow<MetronomePreferencesSnapshot>

    suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot)
}
