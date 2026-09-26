package audio.tembra.data.tuner

import kotlinx.coroutines.flow.Flow

/** The tuner's remembered choices; [snapshot] emits the current value first, then every change (spec FR-005). */
interface TunerPreferences {
    val snapshot: Flow<TunerPreferencesSnapshot>

    suspend fun update(transform: (TunerPreferencesSnapshot) -> TunerPreferencesSnapshot)
}
