package audio.tembra.android.desktop

import android.os.SystemClock
import audio.tembra.ui.perf.PerfAnchors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Full screen from a desktop window (feature 011, spec FR-010a, research R21): the Timeline's menu item "Full screen"
 * makes the window fill the display, "Exit full screen" returns it to the bounds it had, and the music never stops (the
 * demo project plays throughout; its playhead advances by the elapsed time).
 */
class FullScreenTest {

    private var playing = false

    /** Stops the transport if it still plays (the button toggles; the demo also stops at its end). */
    @After
    fun stopPlaying() {
        if (playing && DesktopApp.transport().playing) DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
    }

    @Test
    fun fullScreenFillsTheDisplayAndExitReturnsTheWindow() {
        DesktopWindow.assumeFreeform()
        DesktopApp.grantNotifications()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1000, 700)
        val windowed = DesktopWindow.windowBoundsPx()
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
        // From the start: the session keeps the playhead where an earlier test left it, and a start in the demo's last
        // seconds is refused ("Could not start playback").
        DesktopApp.tap(PerfAnchors.TRANSPORT_SKIP_START)
        DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
        playing = true
        DesktopApp.waitUntil("the transport to play") { DesktopApp.transport().playing }
        val startSeconds = DesktopApp.transport().audiblePositionSeconds
        val started = SystemClock.elapsedRealtimeNanos()

        DesktopApp.tap("timeline.toolbar.overflow.button")
        DesktopApp.findText("Full screen").click()
        val (displayWidth, displayHeight) = DesktopWindow.displaySizePx()
        DesktopApp.waitUntil("the window to fill the display") {
            DesktopWindow.windowBoundsPx().let { it.width() == displayWidth && it.height() == displayHeight }
        }
        assertTrue("playback stopped entering full screen", DesktopApp.transport().playing)

        DesktopApp.tap("timeline.toolbar.overflow.button")
        DesktopApp.findText("Exit full screen").click()
        DesktopApp.waitUntil("the window to return") { DesktopWindow.windowBoundsPx() == windowed }
        assertEquals(windowed, DesktopWindow.windowBoundsPx())

        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1e9
        val advanced = DesktopApp.transport().audiblePositionSeconds - startSeconds
        assertTrue("playback stopped", DesktopApp.transport().playing)
        assertTrue("the playhead advanced $advanced s in $elapsed s", abs(advanced - elapsed) < 0.25)
    }
}
