package audio.tembra.android.desktop

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.window.layout.WindowMetricsCalculator
import audio.tembra.android.MainActivity
import org.junit.Assume.assumeTrue

/**
 * Drives the app's own window on a device with free-form windows (feature 011, research R14): the Android desktop
 * emulators (`Tembra_Desktop_API37`, `Tembra_Desktop_API34`) and a tablet in desktop mode. Shell commands go through
 * [android.app.UiAutomation.executeShellCommand] (the shell user may start and resize any task). Sizes set with
 * [resizeTask] are not clamped to the app's minimum and never remembered; [dragResize] is a real mouse drag of the
 * window's corner, which the platform clamps (research "Hands-on check: Desktop (Preview)", T004).
 */
object DesktopWindow {
    private const val ACTIVITY = "audio.tembra.android.MainActivity"
    private const val TIMEOUT_MS = 10_000L

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    /** Skips the calling test, with a reason the report shows, on a device without free-form window management. */
    fun assumeFreeform() {
        assumeTrue(
            "no free-form windows on this device: run on Tembra_Desktop_API37, Tembra_Desktop_API34 or a tablet in desktop mode",
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
        )
    }

    /** Starts [MainActivity] in a free-form window (windowing mode 5) as the current user and waits until it resumes. */
    fun launchFreeform(): MainActivity {
        shell("am start --user current --windowingMode 5 -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -n ${context.packageName}/$ACTIVITY")
        return awaitResumed()
    }

    /** Brings the app's existing task to the front, the way the launcher does. */
    fun bringAppToFront(): MainActivity {
        shell("am start --user current -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-n ${context.packageName}/$ACTIVITY")
        return awaitResumed()
    }

    /** Opens another app's window (the system settings) in front of this one: the app loses focus but stays visible. */
    fun bringOtherTaskToFront() {
        shell("am start --user current --windowingMode 5 -a android.settings.SETTINGS")
        SystemClock.sleep(1_500)
    }

    /** Closes the window [bringOtherTaskToFront] opened. */
    fun closeOtherTask() {
        shell("am force-stop --user current com.android.settings")
    }

    /** The task id of the app's single task. */
    fun taskId(): Int {
        val tasks = context.getSystemService(ActivityManager::class.java).appTasks
        check(tasks.size == 1) { "expected one task, found ${tasks.size}" }
        return checkNotNull(tasks.single().taskInfo) { "the task has no info" }.taskId
    }

    /** Number of tasks the app has (the single-window rule, FR-010). */
    fun taskCount(): Int = context.getSystemService(ActivityManager::class.java).appTasks.size

    /** Sets the task's bounds in pixels with `am task resize`; not clamped to any minimum, not remembered. */
    fun resizeTask(left: Int, top: Int, right: Int, bottom: Int) {
        shell("am task resize ${taskId()} $left $top $right $bottom")
        DesktopApp.settle()
    }

    /** Resizes the task to [widthDp] × [heightDp] keeping its top-left corner. */
    fun resizeTaskDp(widthDp: Int, heightDp: Int) {
        val bounds = windowBoundsPx()
        val density = context.resources.displayMetrics.density
        resizeTask(bounds.left, bounds.top, bounds.left + (widthDp * density).toInt(), bounds.top + (heightDp * density).toInt())
    }

    /**
     * Drags the window's bottom-right corner with the mouse to ([toX], [toY]) in screen pixels. The resize handle sits
     * on the corner on Android 17 and a few pixels outside it on Android 14 (T004), so the drag starts inside and, when
     * the bounds did not change, again from just outside.
     */
    fun dragResize(toX: Int, toY: Int) {
        val before = windowBoundsPx()
        for (offset in intArrayOf(-2, 4)) {
            shell("input mouse swipe ${before.right + offset} ${before.bottom + offset} $toX $toY 1500")
            SystemClock.sleep(1_000)
            DesktopApp.settle()
            if (windowBoundsPx() != before) return
        }
    }

    /** The resumed activity's window bounds in pixels (screen coordinates). */
    fun windowBoundsPx(activity: Activity = resumedActivity()): Rect {
        var bounds = Rect()
        instrumentation.runOnMainSync { bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds }
        return bounds
    }

    /** The resumed activity's window size in dp, as (width, height). */
    fun windowBoundsDp(activity: Activity = resumedActivity()): Pair<Float, Float> {
        val bounds = windowBoundsPx(activity)
        val density = context.resources.displayMetrics.density
        return bounds.width() / density to bounds.height() / density
    }

    /**
     * The display's size in pixels: the largest window the activity can have. (The app's display metrics follow its
     * free-form window, so they are not the display.)
     */
    fun displaySizePx(activity: Activity = resumedActivity()): Pair<Int, Int> {
        var bounds = Rect()
        instrumentation.runOnMainSync { bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(activity).bounds }
        return bounds.width() to bounds.height()
    }

    /** The [MainActivity] in the RESUMED stage, waiting up to ten seconds for one. */
    fun awaitResumed(): MainActivity {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            resumedOrNull()?.let { return it }
            SystemClock.sleep(100)
        }
        error("MainActivity did not resume within $TIMEOUT_MS ms")
    }

    private fun resumedActivity(): MainActivity = resumedOrNull() ?: awaitResumed()

    private fun resumedOrNull(): MainActivity? {
        var found: MainActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return found
    }

    /** Runs a shell command as the shell user and returns its output. */
    fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
