package audio.tembra.data.metronome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [MetronomePreferences] over a DataStore Preferences file shared with the other features (contracts/metronome-preferences.md). */
class DataStoreMetronomePreferences(private val dataStore: DataStore<Preferences>) : MetronomePreferences {

    private val keys = listOf(
        MetronomePreferenceKeys.TEMPO,
        MetronomePreferenceKeys.SIGNATURE,
        MetronomePreferenceKeys.SUBDIVISION,
        MetronomePreferenceKeys.LEVELS,
        MetronomePreferenceKeys.STYLE,
        MetronomePreferenceKeys.SOUND,
        MetronomePreferenceKeys.CUSTOM_NAME,
        MetronomePreferenceKeys.CUSTOM_PATH,
    ).associateWith(::stringPreferencesKey)

    override val snapshot: Flow<MetronomePreferencesSnapshot> = dataStore.data.map(::decode)

    override suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot) {
        dataStore.edit { preferences ->
            val encoded = transform(decode(preferences)).encode()
            for ((name, key) in keys) {
                val value = encoded[name]
                if (value == null) preferences.remove(key) else preferences[key] = value
            }
        }
    }

    private fun decode(preferences: Preferences): MetronomePreferencesSnapshot =
        MetronomePreferencesSnapshot.decode(keys.mapValues { (_, key) -> preferences[key] })
}
