package audio.tembra.android

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the activity's `OpenDocument` launcher to [AndroidSoundFilePicker] (contracts/click-sounds.md), like
 * [MicrophonePermissionHost]: the graph owns one instance; [MainActivity] attaches its launcher in `onCreate` and
 * detaches in `onDestroy`. Used on the main thread only. The function constructor lets tests substitute the launcher.
 */
class SoundFilePickerHost() {

    /** Test constructor: [launch] stands in for the activity result launcher. */
    internal constructor(launch: (Array<String>) -> Unit) : this() {
        this.launch = launch
    }

    private var launch: ((Array<String>) -> Unit)? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun attach(launcher: ActivityResultLauncher<Array<String>>) {
        launch = { mimeTypes -> launcher.launch(mimeTypes) }
    }

    /** The activity is going away; a pending pick resolves as cancelled. */
    fun detach() {
        launch = null
        pending?.complete(null)
        pending = null
    }

    /** Called from the activity result callback: the picked document, or null when the user backed out. */
    fun onResult(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    /**
     * Opens the document picker for [mimeTypes] and suspends until [onResult]; null at once when no activity is
     * attached, and null at once for a second request while the picker is already open (the first one gets the pick).
     */
    suspend fun open(mimeTypes: Array<String>): Uri? {
        if (pending != null) return null
        val launcher = launch ?: return null
        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        launcher(mimeTypes)
        return deferred.await()
    }
}
