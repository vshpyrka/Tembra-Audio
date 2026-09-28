package audio.tembra.android

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the activity's permission launcher to [AndroidMicrophonePermission] (research R6). The graph owns one
 * instance; [MainActivity] attaches its launcher in `onCreate` and detaches in `onDestroy`. The function constructor
 * lets tests substitute the launcher.
 */
class MicrophonePermissionHost internal constructor(
    private var launch: ((String) -> Unit)?,
    private var rationale: (() -> Boolean)?,
) {
    constructor() : this(null, null)

    /** Test constructor: [launch] stands in for the activity result launcher; no rationale is ever shown. */
    constructor(launch: (String) -> Unit) : this(launch, { false })

    private var pending: CompletableDeferred<Boolean>? = null
    private var asked = false

    fun attach(activity: ComponentActivity, launcher: ActivityResultLauncher<String>) {
        launch = { permission -> launcher.launch(permission) }
        rationale = { activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) }
    }

    fun detach() {
        launch = null
        rationale = null
        pending?.complete(false)
        pending = null
    }

    /** Called from the activity result callback. */
    fun onResult(granted: Boolean) {
        asked = true
        pending?.complete(granted)
        pending = null
    }

    /**
     * Shows the prompt and suspends until [onResult]; false at once when no activity is attached. A request made
     * while one is already pending shares its result instead of launching the prompt again.
     */
    suspend fun launch(): Boolean {
        pending?.let { return it.await() }
        val launcher = launch ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        launcher(Manifest.permission.RECORD_AUDIO)
        return deferred.await()
    }

    /** True after a denial the OS still allows to be re-asked; false before any request. */
    fun shouldShowRationale(): Boolean = rationale?.invoke() ?: false

    /** True once a request has been answered in this process. */
    fun hasAsked(): Boolean = asked
}
