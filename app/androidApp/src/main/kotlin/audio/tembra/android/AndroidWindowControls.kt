package audio.tembra.android

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    /**
     * An enter request the callback called refused while the system may still carry it out: Android 17's window shell
     * approves and runs the transition and the client still hears "denied by system policy" (desktop emulator, window
     * animations off, 2026-11-03). The next multi-window change decides.
     */
    private var enterUndecided = false

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
        val current = activity
        if (enterUndecided && current != null && !current.isInMultiWindowMode) requested = true
        enterUndecided = false
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

    private val mainHandler = Handler(Looper.getMainLooper())

    private val requests = FullScreenRequest(
        send = { enter, onApproved, onRefused ->
            val current = activity
            if (current == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                onRefused(IllegalStateException("no activity able to go full screen"))
            } else {
                val mode = if (enter) Activity.FULLSCREEN_MODE_REQUEST_ENTER else Activity.FULLSCREEN_MODE_REQUEST_EXIT
                current.requestFullscreenMode(
                    mode,
                    object : OutcomeReceiver<Void, Throwable> {
                        override fun onResult(result: Void?) = onApproved()

                        override fun onError(error: Throwable) = onRefused(error)
                    },
                )
            }
        },
        later = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
        windowed = { activity?.isInMultiWindowMode ?: false },
    )

    private fun request(enter: Boolean) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        requests.request(
            enter,
            onApproved = {
                requested = enter
                awaitingModeChange = true
                refresh()
            },
            onRefused = { error ->
                // The system refused twice (the window is not free-form, or another request is pending), or says so for a
                // request it carries out anyway: the window's mode decides, now or at the next change.
                Log.w(TAG, "full-screen request refused", error)
                if (enter) {
                    val current = activity
                    if (current != null && !current.isInMultiWindowMode) requested = true else enterUndecided = true
                }
                refresh()
            },
        )
    }

    private companion object {
        const val TAG = "TembraWindow"
    }
}
