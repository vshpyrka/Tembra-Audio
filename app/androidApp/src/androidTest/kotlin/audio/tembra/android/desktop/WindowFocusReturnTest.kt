package audio.tembra.android.desktop

import androidx.test.uiautomator.By
import audio.tembra.ui.perf.PerfAnchors
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Losing window focus stops nothing and returning restores keyboard focus (feature 011, spec FR-009): with the first
 * track header focused by Tab and the demo project playing, another window comes to the front and goes; the same header
 * still has focus and the playhead never stopped.
 */
class WindowFocusReturnTest {

    private var playing = false

    @After
    fun tidyUp() {
        DesktopWindow.closeOtherTask()
        if (playing) DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
    }

    @Test
    fun theFocusedHeaderKeepsFocusAndThePlayheadKeepsGoing() {
        DesktopWindow.assumeFreeform()
        DesktopApp.grantNotifications()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1200, 800)
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
        DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
        playing = true
        DesktopApp.waitUntil("the transport to play") { DesktopApp.transport().playing }

        val header = By.res("timeline.header.0").focused(true)
        var presses = 0
        while (DesktopApp.device.findObject(header) == null) {
            check(presses++ < 40) { "Tab never reached the first track header" }
            DesktopInput.key("KEYCODE_TAB")
        }
        val before = DesktopApp.transport().audiblePositionSeconds

        DesktopWindow.bringOtherTaskToFront()
        assertTrue("the transport stopped when the window lost focus", DesktopApp.transport().playing)
        DesktopWindow.closeOtherTask()
        DesktopWindow.bringAppToFront()

        DesktopApp.waitUntil("the header to have focus again") { DesktopApp.device.findObject(header) != null }
        val after = DesktopApp.transport()
        assertTrue("the transport stopped", after.playing)
        assertTrue("the playhead did not advance (${after.audiblePositionSeconds} after $before)", after.audiblePositionSeconds > before)
    }
}
