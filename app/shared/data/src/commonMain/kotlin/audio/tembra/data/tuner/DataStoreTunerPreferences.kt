package audio.tembra.data.tuner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [TunerPreferences] over a DataStore Preferences file (contracts/tuner-preferences.md). */
class DataStoreTunerPreferences(private val dataStore: DataStore<Preferences>) : TunerPreferences {

    private val instrument = stringPreferencesKey(TunerPreferenceKeys.INSTRUMENT)
    private val guitarStrings = stringPreferencesKey(TunerPreferenceKeys.GUITAR_STRINGS)
    private val presetId = stringPreferencesKey(TunerPreferenceKeys.PRESET_ID)
    private val mode = stringPreferencesKey(TunerPreferenceKeys.MODE)
    private val loopTone = booleanPreferencesKey(TunerPreferenceKeys.LOOP_TONE)

    override val snapshot: Flow<TunerPreferencesSnapshot> = dataStore.data.map(::decode)

    override suspend fun update(transform: (TunerPreferencesSnapshot) -> TunerPreferencesSnapshot) {
        dataStore.edit { preferences ->
            val next = transform(decode(preferences))
            preferences[instrument] = next.instrument.id
            preferences[guitarStrings] = next.guitarStrings.count.toString()
            preferences[presetId] = next.presetId
            preferences[mode] = next.mode.id
            preferences[loopTone] = next.loopTone
        }
    }

    private fun decode(preferences: Preferences): TunerPreferencesSnapshot = TunerPreferencesSnapshot.decode(
        mapOf(
            TunerPreferenceKeys.INSTRUMENT to preferences[instrument],
            TunerPreferenceKeys.GUITAR_STRINGS to preferences[guitarStrings],
            TunerPreferenceKeys.PRESET_ID to preferences[presetId],
            TunerPreferenceKeys.MODE to preferences[mode],
        ),
        preferences[loopTone],
    )
}
