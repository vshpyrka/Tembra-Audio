package audio.tembra.perf.journeys

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/**
 * The timeline journey (feature 009, contracts/perf-instrumentation.md): open Projects, open the demo card, wait for
 * every visible overview, play eight bars, seek on the ruler, pinch the first lane in and out, split the first clip,
 * open and close the first track's settings, toggle the metronome twice, on a phone open the master volume pop-up,
 * drag its slider and close it, drag the overview strip's window there and back, drag the strip up and down from its centre to zoom in and
 * out, double-tap it to fit the whole project (all three unless the device hid the strip), pause, skip to start.
 */
class TimelineJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_PROJECTS)
        device.anchor(Anchors.PROJECTS_ROOT)
        device.tap(Anchors.PROJECTS_CARD_DEMO)
        device.anchor(Anchors.TIMELINE_ROOT)
        // The waveforms: the screen carries a 1 dp marker node once every visible overview is built (the
        // `stateDescription` "ready" of the contract is not reachable through UiAutomator's selectors).
        check(device.wait(Until.hasObject(By.res(Anchors.TIMELINE_READY_MARKER)), READY_TIMEOUT_MS)) {
            "the timeline's overviews were not ready within $READY_TIMEOUT_MS ms"
        }
        device.tap(Anchors.TRANSPORT_PLAY)
        val readout = device.anchor(Anchors.TRANSPORT_READOUT)
        // Eight bars at 124 BPM are 15.5 s: wait until the readout passes 00:15.
        check(readout.wait(Until.descMatches("00:1[5-9].*|00:[2-5].*"), PLAY_TIMEOUT_MS)) { "the readout never passed 00:15" }
        val ruler = device.anchor(Anchors.TIMELINE_RULER)
        val bounds = ruler.visibleBounds
        // Clear of the ruler's left third (the tempo and signature pills that overlaid it before design iteration D1
        // would have opened a menu), and early enough in the demo's clips (they end at bar 17 of the 32 the fitted ruler
        // shows) that the playhead, still moving through the two pinches, is inside the first clip when the split comes.
        device.click(bounds.left + bounds.width() * 2 / 5, bounds.centerY())
        device.waitForIdle()
        val lane = device.anchor(Anchors.TIMELINE_LANE_FIRST)
        lane.pinchOpen(PINCH_PERCENT, PINCH_SPEED)
        device.waitForIdle()
        lane.pinchClose(PINCH_PERCENT, PINCH_SPEED)
        device.waitForIdle()
        // The split needs the playhead inside the first clip whatever zoom the pinches (and the saved view state) left:
        // back to the start, then past bar 1 while still playing (00:02 is bar 2 at 124 BPM), both through anchors.
        device.tap(Anchors.TRANSPORT_SKIP_START)
        check(readout.wait(Until.descMatches("00:0[01].*"), PLAY_TIMEOUT_MS)) { "skip-to-start never reached the readout" }
        check(readout.wait(Until.descMatches("00:0[2-9].*"), PLAY_TIMEOUT_MS)) { "the readout never passed 00:02 after skip-to-start" }
        device.tap(Anchors.TIMELINE_CLIP_FIRST)
        device.tap(Anchors.TIMELINE_SPLIT)
        // The split is undone at once: the document persists between iterations and rounds, and a demo that keeps
        // its five clips is what every later iteration (and `timeline.clip.first`) relies on.
        device.tap(Anchors.TIMELINE_UNDO)
        device.tap(Anchors.TIMELINE_TRACK_MENU_FIRST)
        device.tap(Anchors.TIMELINE_TRACK_SETTINGS)
        device.tap(Anchors.TIMELINE_SETTINGS_CLOSE)
        device.anchor(Anchors.TIMELINE_ROOT)
        device.tap(Anchors.TRANSPORT_METRONOME)
        device.tap(Anchors.TRANSPORT_METRONOME)
        adjustMasterVolume(device)
        dragOverviewWindow(device)
        zoomOverviewStrip(device)
        fitOverviewStrip(device)
        device.tap(Anchors.TRANSPORT_PLAY)
        device.tap(Anchors.TRANSPORT_SKIP_START)
    }

    /**
     * Phones only (design iteration D1): the speaker button opens the master volume pop-up (`transport.masterPopup`), its
     * slider is dragged a little, a tap outside the pop-up closes it, and undo restores the volume: the drag is one undo
     * step, and the document persists between iterations. From 600 dp of bar width the slider is inline and there is no
     * button, so the step is skipped.
     */
    private fun adjustMasterVolume(device: UiDevice) {
        val button = device.findObject(By.res(Anchors.TRANSPORT_MASTER_BUTTON)) ?: return
        // The readout is outside the pop-up, which opens above the bar; its bounds are read before the pop-up shows.
        val outside = device.anchor(Anchors.TRANSPORT_READOUT).visibleBounds
        button.click()
        device.waitForIdle()
        // From the slider's centre (its node's edges are Material's focus-ring room, outside the touch area), an eighth of
        // its width to the right.
        val slider = device.anchor(Anchors.TRANSPORT_MASTER).visibleBounds
        device.swipe(slider.centerX(), slider.centerY(), slider.centerX() + slider.width() / 8, slider.centerY(), SWIPE_STEPS)
        device.waitForIdle()
        // A real tap through the window manager: the focusable pop-up sees it as outside itself and closes.
        device.click(outside.centerX(), outside.centerY())
        check(device.wait(Until.gone(By.res(Anchors.TRANSPORT_MASTER)), CLOSE_TIMEOUT_MS)) { "the master volume pop-up did not close" }
        device.tap(Anchors.TIMELINE_UNDO)
    }

    /**
     * The overview strip shows unless the device hid it from the overflow menu (spec FR-015; T151), and then the step is
     * skipped: the strip's window is dragged from its centre by a quarter of its width, inside `timeline.scroll`, then
     * back by the distance it moved (it stops at the project's end), so the view returns.
     */
    private fun dragOverviewWindow(device: UiDevice) {
        device.findObject(By.res(Anchors.TIMELINE_OVERVIEW)) ?: return
        val window = device.anchor(Anchors.TIMELINE_OVERVIEW_WINDOW).visibleBounds
        val y = window.centerY()
        device.swipe(window.centerX(), y, window.centerX() + window.width() / 4, y, SWIPE_STEPS)
        device.waitForIdle()
        val moved = device.anchor(Anchors.TIMELINE_OVERVIEW_WINDOW).visibleBounds
        val distance = moved.left - window.left
        if (distance != 0) {
            device.swipe(moved.centerX(), y, moved.centerX() - distance, y, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    /**
     * Step 8c, with the strip shown: a press at the strip's centre dragged straight up by a third of the screen's
     * height zooms in about 2^(h/3/60 dp) around the window's centre (the drag locks to its first direction, and
     * straight vertical picks zoom), then the same drag straight down zooms back out to about the same level. The
     * move events are 5 ms apart, [ZOOM_STEPS] of them, so it is a drag and not a fling.
     */
    private fun zoomOverviewStrip(device: UiDevice) {
        val strip = device.findObject(By.res(Anchors.TIMELINE_OVERVIEW)) ?: return
        val bounds = strip.visibleBounds
        val x = bounds.centerX()
        val y = bounds.centerY()
        val distance = device.displayHeight / 3
        device.swipe(x, y, x, y - distance, ZOOM_STEPS)
        device.waitForIdle()
        device.swipe(x, y - distance, x, y, ZOOM_STEPS)
        device.waitForIdle()
    }

    /**
     * Step 8d, with the strip shown: a double tap at the strip's centre animates over 300 ms to the fit (the whole project
     * from its start, the window spanning the strip), so every iteration ends at the same view whatever zoom the
     * earlier steps and the saved view state left. The strip's width is then compared with the window's.
     *
     * The second tap must land within Compose's double-tap timeout (300 ms of the first). Two `UiDevice.click` calls
     * each wait for idle and cross the adb-driven shell, which may exceed that, so the events are injected straight
     * through `UiAutomation.injectInputEvent` (synchronously): down, up, a 50 ms gap, down, up, stamped with
     * `SystemClock.uptimeMillis()` times on a touchscreen source, from the instrumentation process with no round trip
     * between the taps.
     */
    private fun fitOverviewStrip(device: UiDevice) {
        val strip = device.findObject(By.res(Anchors.TIMELINE_OVERVIEW)) ?: return
        val bounds = strip.visibleBounds
        doubleTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        val stripWidth = bounds.width()
        val deadline = SystemClock.uptimeMillis() + FIT_TIMEOUT_MS
        var windowWidth = 0
        do {
            device.waitForIdle()
            windowWidth = device.anchor(Anchors.TIMELINE_OVERVIEW_WINDOW).visibleBounds.width()
            if (kotlin.math.abs(windowWidth - stripWidth) <= FIT_TOLERANCE_PX) return
            SystemClock.sleep(FIT_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("the double tap did not fit the overview strip: the window is $windowWidth px wide, the strip $stripWidth px")
    }

    private fun doubleTap(x: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(2) { tap ->
            if (tap > 0) SystemClock.sleep(DOUBLE_TAP_GAP_MS)
            val down = SystemClock.uptimeMillis()
            inject(automation, down, down, MotionEvent.ACTION_DOWN, x, y)
            inject(automation, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
        }
    }

    private fun inject(automation: android.app.UiAutomation, down: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            check(automation.injectInputEvent(event, true)) { "the touch event was not injected" }
        } finally {
            event.recycle()
        }
    }

    private fun fail(message: String): Nothing = throw IllegalStateException(message)

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val PINCH_PERCENT = 0.6f
        const val PINCH_SPEED = 400
        /** Move events of a slider or strip drag, 5 ms apart. */
        const val SWIPE_STEPS = 20
        const val CLOSE_TIMEOUT_MS = 5_000L
        /** Move events of the strip's vertical zoom drags, 5 ms apart. */
        const val ZOOM_STEPS = 60
        const val DOUBLE_TAP_GAP_MS = 50L
        const val FIT_TIMEOUT_MS = 4_000L
        const val FIT_POLL_MS = 100L
        const val FIT_TOLERANCE_PX = 4
    }
}
