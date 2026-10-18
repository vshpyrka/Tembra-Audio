package audio.tembra.android

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the activity's `OpenDocument` and `OpenMultipleDocuments` launchers to [AndroidAudioFilePicker]
 * (contracts/project-storage.md; generalised from the metronome's picker host), like [MicrophonePermissionHost]: the
 * graph owns one instance; [MainActivity] attaches its launchers in `onCreate` and detaches in `onDestroy`. Used on
 * the main thread only. The function constructor lets tests substitute the launchers.
 */
class AudioFilePickerHost() {

    /** Test constructor: [launchOne] and [launchMany] stand in for the activity result launchers. */
    internal constructor(launchOne: (Array<String>) -> Unit, launchMany: (Array<String>) -> Unit) : this() {
        this.launchOne = launchOne
        this.launchMany = launchMany
    }

    private var launchOne: ((Array<String>) -> Unit)? = null
    private var launchMany: ((Array<String>) -> Unit)? = null
    private var pending: CompletableDeferred<List<Uri>?>? = null

    fun attach(one: ActivityResultLauncher<Array<String>>, many: ActivityResultLauncher<Array<String>>) {
        launchOne = { mimeTypes -> one.launch(mimeTypes) }
        launchMany = { mimeTypes -> many.launch(mimeTypes) }
    }

    /** The activity is going away; a pending pick resolves as cancelled. */
    fun detach() {
        launchOne = null
        launchMany = null
        pending?.complete(null)
        pending = null
    }

    /** Called from the single-document result callback: the picked document, or null when the user backed out. */
    fun onResult(uri: Uri?) {
        pending?.complete(uri?.let(::listOf))
        pending = null
    }

    /** Called from the multiple-documents result callback: the picked documents in picker order; empty when the user backed out. */
    fun onResult(uris: List<Uri>) {
        pending?.complete(uris.ifEmpty { null })
        pending = null
    }

    /**
     * Opens the picker for [mimeTypes] and suspends until a result arrives; null at once when no activity is attached,
     * and null at once for a second request while the picker is already open (the first one gets the pick).
     */
    suspend fun open(mimeTypes: Array<String>, multiple: Boolean): List<Uri>? {
        if (pending != null) return null
        val launcher = (if (multiple) launchMany else launchOne) ?: return null
        val deferred = CompletableDeferred<List<Uri>?>()
        pending = deferred
        launcher(mimeTypes)
        return deferred.await()
    }
}
