package audio.tembra.android

import android.app.Activity
import android.os.Build
import android.os.OutcomeReceiver
import android.util.Log
import audio.tembra.ui.platform.WindowControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full screen for a desktop window (feature 011, spec FR-010a, research R21): `Activity.requestFullscreenMode`, API
 * 34+, offered while the activity is in a free-form or split window (Android reports both as multi-window mode) and
 * while the app holds it full screen. The graph owns one instance; [MainActivity] attaches itself in `onCreate`,
 * refreshes on every configuration and multi-window change, and detaches in `onDestroy`.
 */
class AndroidWindowControls : WindowControls {
    private var activity: Activity? = null
    private val _fullScreenAvailable = MutableStateFlow(false)
    private val _isFullScreen = MutableStateFlow(false)
    override val fullScreenAvailable: StateFlow<Boolean> = _fullScreenAvailable.asStateFlow()
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    /** The app asked for full screen and the system accepted; true until the window is a desktop window again. */
    private var requested = false

    /** Accepted but the activity still reports its old mode: the multi-window change has not arrived yet. */
    private var awaitingModeChange = false

    fun attach(activity: Activity) {
        this.activity = activity
        refresh()
    }

    fun detach(activity: Activity) {
        if (this.activity !== activity) return
        this.activity = null
        _fullScreenAvailable.value = false
    }

    /** The activity's multi-window mode changed: whatever the app asked for has now taken effect. */
    fun onMultiWindowModeChanged() {
        awaitingModeChange = false
        refresh()
    }

    /** Re-reads the window mode: a window the user restored with the system's own controls is no longer full screen. */
    fun refresh() {
        val current = activity ?: return
        val windowed = current.isInMultiWindowMode
        if (windowed && requested && !awaitingModeChange) requested = false
        _isFullScreen.value = requested && !windowed
        _fullScreenAvailable.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (windowed || requested)
    }

    override fun enterFullScreen() = request(enter = true)

    override fun exitFullScreen() = request(enter = false)

    private fun request(enter: Boolean) {
        val current = activity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val mode = if (enter) Activity.FULLSCREEN_MODE_REQUEST_ENTER else Activity.FULLSCREEN_MODE_REQUEST_EXIT
        current.requestFullscreenMode(
            mode,
            object : OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) {
                    requested = enter
                    awaitingModeChange = true
                    refresh()
                }

                override fun onError(error: Throwable) {
                    // The system refused (the window is not free-form, or another request is pending): nothing changed.
                    Log.w(TAG, "full-screen request refused", error)
                    refresh()
                }
            },
        )
    }

    private companion object {
        const val TAG = "TembraWindow"
    }
}
