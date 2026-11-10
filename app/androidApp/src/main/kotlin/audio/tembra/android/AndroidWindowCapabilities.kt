package audio.tembra.android

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import audio.tembra.ui.platform.WindowCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A second window of the app on Android (feature 012, spec FR-021a, research R10): offered where the device manages
 * free-form windows (the desktop emulators, a Chromebook, a tablet in desktop mode), the mixer's window is
 * [MixerWindowActivity] in a task of its own (`singleTask`, its own affinity), started in a new task with launch bounds
 * the first time and brought to the front afterwards. The graph owns one instance; the activity reports its life here
 * so the Timeline knows the window is open.
 */
class AndroidWindowCapabilities(private val context: Context) : WindowCapabilities {
    override val multipleWindows: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private val _mixerWindowOpen = MutableStateFlow(false)
    override val mixerWindowOpen: StateFlow<Boolean> = _mixerWindowOpen.asStateFlow()

    private var window: Activity? = null

    override fun openMixerWindow() {
        if (!multipleWindows) return
        val intent = Intent(context, MixerWindowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (window == null) {
            // A window of its own beside the timeline's: a new task at a desk-sized default; a reopen finds the task and
            // brings it forward (the activity's launch mode).
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            options.launchBounds = defaultBounds()
        }
        context.startActivity(intent, options.toBundle())
    }

    fun windowCreated(activity: Activity) {
        window = activity
        _mixerWindowOpen.value = true
    }

    fun windowDestroyed(activity: Activity) {
        if (window !== activity) return
        window = null
        _mixerWindowOpen.value = false
    }

    /** The timeline window closed for good: the mixer's window and its task go with it (spec FR-021a, "the project's close closes both"). */
    fun closeMixerWindow() {
        window?.finishAndRemoveTask()
    }

    /** 720 × 480 dp, centred on the display the app knows, so the window opens beside the timeline's rather than over it. */
    private fun defaultBounds(): Rect {
        val metrics = context.resources.displayMetrics
        val width = (WINDOW_WIDTH_DP * metrics.density).toInt().coerceAtMost(metrics.widthPixels)
        val height = (WINDOW_HEIGHT_DP * metrics.density).toInt().coerceAtMost(metrics.heightPixels)
        val left = (metrics.widthPixels - width) / 2
        val top = (metrics.heightPixels - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    private companion object {
        const val WINDOW_WIDTH_DP = 720
        const val WINDOW_HEIGHT_DP = 480
    }
}
