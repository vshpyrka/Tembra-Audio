package audio.tembra.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import audio.tembra.android.desktop.DesktopWindow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One window (feature 011, spec FR-010): opening the app again from the launcher while it runs brings the existing
 * window to the front: the same task, one task, one [MainActivity].
 */
class SingleWindowTest {

    @Test
    fun launchingAgainBringsTheSameWindowForward() {
        DesktopWindow.assumeFreeform()
        DesktopWindow.launchFreeform()
        val task = DesktopWindow.taskId()
        DesktopWindow.bringOtherTaskToFront()
        DesktopWindow.bringAppToFront()
        assertEquals(1, DesktopWindow.taskCount())
        assertEquals(task, DesktopWindow.taskId())
        var activities = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            activities = Stage.entries.filter { it != Stage.DESTROYED }
                .sumOf { stage -> monitor.getActivitiesInStage(stage).count { it is MainActivity } }
        }
        assertEquals("more than one MainActivity is alive", 1, activities)
        DesktopWindow.closeOtherTask()
    }
}
