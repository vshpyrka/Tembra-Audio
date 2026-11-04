package audio.tembra.android

import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.android.desktop.DesktopApp
import audio.tembra.android.desktop.DesktopWindow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What "in the foreground" means for a desktop window (feature 011, spec FR-006/FR-009, research R4): the tuner's
 * listening follows `ON_START`/`ON_STOP` of this lifecycle (`TunerScreen`'s `LifecycleStartEffect`, pinned by
 * `ListeningForegroundTest`). Another window taking focus leaves the app visible and at least STARTED, so listening and
 * a looping tone go on; minimising stops it (CREATED); restoring starts it again.
 */
class WindowForegroundTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun closeTheOtherWindow() = DesktopWindow.closeOtherTask()

    private fun state(activity: MainActivity): Lifecycle.State {
        var state = Lifecycle.State.INITIALIZED
        instrumentation.runOnMainSync { state = activity.lifecycle.currentState }
        return state
    }

    @Test
    fun losingFocusKeepsTheAppStartedAndMinimisingStopsIt() {
        DesktopWindow.assumeFreeform()
        val activity = DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(700, 600)

        DesktopWindow.bringOtherTaskToFront()
        assertTrue("another window's focus stopped the app (${state(activity)})", state(activity).isAtLeast(Lifecycle.State.STARTED))

        // Minimise: in desktop windowing, moving the task to the back minimises its window.
        instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
        DesktopApp.waitUntil("the minimised window to stop") { !state(activity).isAtLeast(Lifecycle.State.STARTED) }

        val restored = DesktopWindow.bringAppToFront()
        DesktopApp.waitUntil("the restored window to start") { state(restored).isAtLeast(Lifecycle.State.STARTED) }
    }
}
