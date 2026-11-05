package audio.tembra.android.desktop

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
        // The button toggles, and the demo also stops at its end: stop it only while it plays.
        if (playing && DesktopApp.transport().playing) DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
    }

    @Test
    fun theFocusedHeaderKeepsFocusAndThePlayheadKeepsGoing() {
        DesktopWindow.assumeFreeform()
        DesktopApp.grantNotifications()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1200, 800)
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
        // From the start: the session keeps the playhead where an earlier test left it, and a start in the demo's last
        // seconds is refused ("Could not start playback").
        DesktopApp.tap(PerfAnchors.TRANSPORT_SKIP_START)
        DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
        playing = true
        DesktopApp.waitUntil("the transport to play") { DesktopApp.transport().playing }

        val header = By.res("timeline.header.0").focused(true)
        // The keys go to the focused window: after the resize it is made the app's again, as a click on it would. Each
        // Tab gets a moment to reach the accessibility tree (without window animations it can lag a press).
        DesktopWindow.bringAppToFront()
        var presses = 0
        while (!DesktopApp.device.wait(Until.hasObject(header), TAB_SETTLE_MS)) {
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

    private companion object {
        const val TAB_SETTLE_MS = 300L
    }
}
