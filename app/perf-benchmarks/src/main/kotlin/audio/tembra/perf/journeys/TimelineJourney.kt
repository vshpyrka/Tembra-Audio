package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/**
 * The timeline journey (feature 009, contracts/perf-instrumentation.md): open Projects, open the demo card, wait for
 * every visible overview, play eight bars, seek on the ruler, pinch the first lane in and out, split the first clip,
 * open and close the first track's settings, toggle the metronome twice, on a phone open the master volume pop-up,
 * drag its slider and close it, drag the overview strip's window there and back (unless the device hid the strip),
 * pause, skip to start.
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

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val PINCH_PERCENT = 0.6f
        const val PINCH_SPEED = 400
        /** Move events of a slider or strip drag, 5 ms apart. */
        const val SWIPE_STEPS = 20
        const val CLOSE_TIMEOUT_MS = 5_000L
    }
}
