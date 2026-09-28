package audio.tembra.data.tuner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/**
 * The app's one preferences file, `tembra.preferences_pb` (research R10); one instance per process (`SingleIn`).
 * [tunerPreferences] is what the UI graph calls so DataStore types stay inside this module. DataStore needs a
 * [CoroutineScope] to run on, and the caller owns it: the application graph provides the process-long one, and tests
 * cancel their own to close a store between runs.
 */
object TembraDataStore {
    private const val FILE_NAME = "tembra.preferences_pb"

    fun preferences(directory: String, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope) { "$directory/$FILE_NAME".toPath() }

    /** The tuner's remembered choices over [preferences] in [directory], running on [scope]. */
    fun tunerPreferences(directory: String, scope: CoroutineScope): TunerPreferences = DataStoreTunerPreferences(preferences(directory, scope))
}
