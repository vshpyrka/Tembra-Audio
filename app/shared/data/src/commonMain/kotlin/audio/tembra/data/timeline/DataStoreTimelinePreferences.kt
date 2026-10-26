package audio.tembra.data.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import audio.tembra.core.project.ZoomControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [TimelinePreferences] over the DataStore Preferences file the other features share; an absent or unknown id reads as the default. */
class DataStoreTimelinePreferences(private val dataStore: DataStore<Preferences>) : TimelinePreferences {

    private val zoomControlKey = stringPreferencesKey(TimelinePreferenceKeys.ZOOM_CONTROL)

    // The file is shared: a tuner or metronome write emits too, and only this key's changes matter here.
    override val zoomControl: Flow<ZoomControl> = dataStore.data
        .map { preferences -> preferences[zoomControlKey]?.let(ZoomControl::byId) ?: ZoomControl.Default }
        .distinctUntilChanged()

    override suspend fun setZoomControl(control: ZoomControl) {
        dataStore.edit { preferences -> preferences[zoomControlKey] = control.id }
    }
}
