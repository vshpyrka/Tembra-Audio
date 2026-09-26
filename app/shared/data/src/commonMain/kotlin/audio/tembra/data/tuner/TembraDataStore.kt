package audio.tembra.data.tuner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * The app's one preferences file, `tembra.preferences_pb` (research R10); one instance per process (`SingleIn`).
 * [tunerPreferences] is what the UI graph calls so DataStore types stay inside this module.
 */
object TembraDataStore {
    private const val FILE_NAME = "tembra.preferences_pb"

    fun preferences(
        directory: String,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) { "$directory/$FILE_NAME".toPath() }

    /** The tuner's remembered choices over [preferences] in [directory]. */
    fun tunerPreferences(directory: String): TunerPreferences = DataStoreTunerPreferences(preferences(directory))
}
