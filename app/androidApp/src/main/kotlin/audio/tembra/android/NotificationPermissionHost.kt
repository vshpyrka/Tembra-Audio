package audio.tembra.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Asks for the notifications permission the first time the metronome's playback goes to the background service in a
 * session (Android 13+). Without it the service's "Running" notification and its Stop action never show, which was the
 * B12 ruling until 2026-10-15 (contracts/background-playback.md). Playback never waits for the answer; a grant re-posts
 * the notification of the service already running through [onGranted]. The graph owns one instance; [MainActivity]
 * attaches its launcher in `onCreate` and detaches in `onDestroy`, like [MicrophonePermissionHost].
 */
class NotificationPermissionHost internal constructor(
    private val sdk: Int = Build.VERSION.SDK_INT,
    private val onGranted: () -> Unit,
) {
    constructor(onGranted: () -> Unit) : this(Build.VERSION.SDK_INT, onGranted)

    private var isGranted: (() -> Boolean)? = null
    private var launch: ((String) -> Unit)? = null
    private var asked = false

    fun attach(activity: ComponentActivity, launcher: ActivityResultLauncher<String>) = attach(
        isGranted = { ContextCompat.checkSelfPermission(activity, PERMISSION) == PackageManager.PERMISSION_GRANTED },
        launch = { permission -> launcher.launch(permission) },
    )

    /** Test seam: [isGranted] and [launch] stand in for the permission check and the activity result launcher. */
    internal fun attach(isGranted: () -> Boolean, launch: (String) -> Unit) {
        this.isGranted = isGranted
        this.launch = launch
    }

    fun detach() {
        isGranted = null
        launch = null
    }

    /** Shows the prompt once per process, on Android 13+, while notifications are not allowed and an activity is attached. */
    fun requestOnceForPlayback() {
        if (sdk < Build.VERSION_CODES.TIRAMISU || asked) return
        val granted = isGranted ?: return
        val launcher = launch ?: return
        if (granted()) return
        asked = true
        launcher(PERMISSION)
    }

    /** Called from the activity result callback. */
    fun onResult(granted: Boolean) {
        if (granted) onGranted()
    }

    private companion object {
        const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS
    }
}
