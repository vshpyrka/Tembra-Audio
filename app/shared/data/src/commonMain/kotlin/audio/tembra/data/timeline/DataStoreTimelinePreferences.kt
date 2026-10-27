package audio.tembra.data.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [TimelinePreferences] over the DataStore Preferences file the other features share; an absent value reads as shown. */
class DataStoreTimelinePreferences(private val dataStore: DataStore<Preferences>) : TimelinePreferences {

    private val overviewStripKey = booleanPreferencesKey(TimelinePreferenceKeys.OVERVIEW_STRIP)
    private val retiredZoomControlKey = stringPreferencesKey(TimelinePreferenceKeys.RETIRED_ZOOM_CONTROL)

    // The file is shared: a tuner or metronome write emits too, and only this key's changes matter here.
    override val overviewStripShown: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[overviewStripKey] ?: true }
        .distinctUntilChanged()

    override suspend fun setOverviewStripShown(shown: Boolean) {
        dataStore.edit { preferences ->
            preferences[overviewStripKey] = shown
            // The comparison's choice is dropped in the same write, so a device keeps no stale key (T144).
            preferences.remove(retiredZoomControlKey)
        }
    }
}
