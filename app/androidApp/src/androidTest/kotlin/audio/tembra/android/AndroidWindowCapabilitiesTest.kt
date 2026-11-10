package audio.tembra.android

import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import audio.tembra.android.desktop.DesktopWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The second window on Android (feature 012, spec FR-021a, research R10): the capability follows the device's free-form
 * window management (true on the desktop emulators and a tablet in desktop mode, false on a phone), and on such a device
 * "Open in a window" starts [MixerWindowActivity] in a task of its own, which reports itself open and, once finished,
 * closed. Skipped on a device without free-form windows, as feature 011's desktop tests are.
 */
class AndroidWindowCapabilitiesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val graph: AndroidAppGraph get() = (context.applicationContext as TembraApplication).appGraph

    @Test
    fun multipleWindowsFollowsTheFreeformFeature() {
        val freeform = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        assertEquals(freeform, graph.androidWindowCapabilities.multipleWindows)
        assertEquals(freeform, AndroidWindowCapabilities(context).multipleWindows)
    }

    @Test
    fun openingTheMixerWindowStartsItsOwnTaskAndClosingItReportsBack() {
        DesktopWindow.assumeFreeform()
        DesktopWindow.launchFreeform()
        val capabilities = graph.androidWindowCapabilities
        assertFalse("the mixer window reads open before it was opened", capabilities.mixerWindowOpen.value)
        instrumentation.runOnMainSync { capabilities.openMixerWindow() }
        val window = awaitResumed<MixerWindowActivity>()
        assertTrue("the window did not report itself open", capabilities.mixerWindowOpen.value)
        assertEquals("the mixer window has no task of its own", 2, DesktopWindow.taskCount())
        // Opened again while open: the same window comes forward, no second task (the count settles a moment later
        // on Android 14, where the task list is re-read while the window comes to the front).
        instrumentation.runOnMainSync { capabilities.openMixerWindow() }
        awaitTaskCount(2)
        assertEquals(2, DesktopWindow.taskCount())
        assertTrue("the window closed on its second open", capabilities.mixerWindowOpen.value)
        instrumentation.runOnMainSync { window.finishAndRemoveTask() }
        awaitClosed(capabilities)
        // Android 14 keeps the removed task in the app's task list as not running for a while: the helper counts running
        // tasks, and the count settles a moment after the activity's destroy.
        awaitTaskCount(1)
        assertEquals(1, DesktopWindow.taskCount())
    }

    private fun awaitTaskCount(expected: Int) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline && DesktopWindow.taskCount() != expected) SystemClock.sleep(100)
    }

    private inline fun <reified T : Activity> awaitResumed(): T {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var found: T? = null
            instrumentation.runOnMainSync {
                found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<T>().firstOrNull()
            }
            found?.let { return it }
            SystemClock.sleep(100)
        }
        error("${T::class.simpleName} did not resume within $TIMEOUT_MS ms")
    }

    private fun awaitClosed(capabilities: AndroidWindowCapabilities) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (!capabilities.mixerWindowOpen.value) return
            SystemClock.sleep(100)
        }
        error("the mixer window still reads open $TIMEOUT_MS ms after it finished")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
