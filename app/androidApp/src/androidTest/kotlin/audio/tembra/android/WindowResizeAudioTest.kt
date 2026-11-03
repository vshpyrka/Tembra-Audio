package audio.tembra.android

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.android.desktop.DesktopApp
import audio.tembra.android.desktop.DesktopWindow
import audio.tembra.ui.perf.PerfAnchors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Fifty window changes while the music plays (feature 011, spec FR-006, SC-002; contracts/window-behaviour.md): in a
 * free-form window, resizes across the 600 and 840 dp classes, maximise and restore, full screen and back. The engine's
 * xrun and starvation counters do not move, the metronome's click count matches the elapsed time within one click, and
 * a playing project's playhead keeps advancing without a restart. Runs on the desktop emulators and a tablet in desktop
 * mode; skips elsewhere.
 */
class WindowResizeAudioTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun openTheAppInAWindow() {
        DesktopWindow.assumeFreeform()
        DesktopApp.grantNotifications()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1000, 700)
    }

    /** The anchor whose button stops what the test started (the same button starts and stops). */
    private var stopWith: String? = null

    @After
    fun stopTheMusic() {
        stopWith?.let(DesktopApp::tap)
    }

    @Test
    fun theMetronomeKeepsTimeThroughFiftyWindowChanges() {
        DesktopApp.tap(PerfAnchors.NAV_METRONOME)
        DesktopApp.tap(PerfAnchors.METRONOME_START_STOP)
        stopWith = PerfAnchors.METRONOME_START_STOP
        DesktopApp.waitUntil("the metronome to run") { DesktopApp.tick().running && DesktopApp.tick().tickIndex > 1 }
        val startTick = DesktopApp.tick()
        val startHealth = DesktopApp.health()
        val started = SystemClock.elapsedRealtimeNanos()

        makeFiftyWindowChanges()

        val endTick = DesktopApp.tick()
        val elapsed = SystemClock.elapsedRealtimeNanos() - started
        val endHealth = DesktopApp.health()
        assertTrue("the metronome stopped during the window changes", endTick.running)
        assertEquals("xruns during the window changes", startHealth.xruns, endHealth.xruns)
        assertEquals("starvation during the window changes", startHealth.starvation, endHealth.starvation)
        val expected = elapsed.toDouble() / startTick.beatPeriodNanos
        val counted = (endTick.tickIndex - startTick.tickIndex).toDouble()
        assertTrue("counted $counted clicks in ${elapsed / 1_000_000} ms, expected $expected ± 1", abs(counted - expected) <= 1.0)
    }

    @Test
    fun aPlayingProjectKeepsGoingThroughFiftyWindowChanges() {
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
        DesktopApp.tap(PerfAnchors.TRANSPORT_PLAY)
        stopWith = PerfAnchors.TRANSPORT_PLAY
        DesktopApp.waitUntil("the transport to play") { DesktopApp.transport().playing && DesktopApp.transport().audiblePositionSeconds > 0.2 }
        val startHealth = DesktopApp.health()
        val startSeconds = DesktopApp.transport().audiblePositionSeconds
        val started = SystemClock.elapsedRealtimeNanos()

        var last = startSeconds
        makeFiftyWindowChanges { step ->
            val now = DesktopApp.transport()
            assertTrue("the transport stopped at change $step", now.playing)
            assertTrue("the playhead went back at change $step (${now.audiblePositionSeconds} < $last)", now.audiblePositionSeconds >= last)
            last = now.audiblePositionSeconds
        }

        val elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - started) / 1e9
        val advanced = DesktopApp.transport().audiblePositionSeconds - startSeconds
        val endHealth = DesktopApp.health()
        assertEquals("xruns during the window changes", startHealth.xruns, endHealth.xruns)
        assertEquals("starvation during the window changes", startHealth.starvation, endHealth.starvation)
        // The demo project is 31 s long and loops nowhere: the changes take less, so the playhead only moves forward.
        assertTrue("the playhead advanced $advanced s in $elapsedSeconds s", abs(advanced - elapsedSeconds) < 0.25)
    }

    /**
     * Fifty changes: resizes across 600 and 840 dp in both directions, maximise (the whole display) and restore, and
     * full screen and back through the app's window controls; [afterEach] runs after every change.
     */
    private fun makeFiftyWindowChanges(afterEach: (Int) -> Unit = {}) {
        val sizes = listOf(500 to 700, 700 to 600, 900 to 650, 1300 to 800, 640 to 520, 860 to 700)
        val (displayWidth, displayHeight) = DesktopWindow.displaySizePx()
        val controls = DesktopApp.graph.windowControls
        for (step in 1..50) {
            when (step % 10) {
                3 -> DesktopWindow.resizeTask(0, 0, displayWidth, displayHeight)
                7 -> {
                    instrumentation.runOnMainSync { controls.enterFullScreen() }
                    DesktopApp.waitUntil("full screen") { controls.isFullScreen.value }
                }
                8 -> {
                    instrumentation.runOnMainSync { controls.exitFullScreen() }
                    DesktopApp.waitUntil("full screen to end") { !controls.isFullScreen.value }
                }
                else -> {
                    val (width, height) = sizes[step % sizes.size]
                    DesktopWindow.resizeTaskDp(width, height)
                }
            }
            SystemClock.sleep(150)
            afterEach(step)
        }
    }
}
