package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/**
 * The timeline journey (feature 009, contracts/perf-instrumentation.md): open Projects, open the demo card, wait for
 * every visible overview, play eight bars, seek on the ruler, pinch the first lane in and out, split the first clip,
 * open and close the first track's settings, toggle the metronome twice, pause, skip to start.
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
        device.click(bounds.left + bounds.width() / 4, bounds.centerY())
        device.waitForIdle()
        val lane = device.anchor(Anchors.TIMELINE_LANE_FIRST)
        lane.pinchOpen(PINCH_PERCENT, PINCH_SPEED)
        device.waitForIdle()
        lane.pinchClose(PINCH_PERCENT, PINCH_SPEED)
        device.waitForIdle()
        device.tap(Anchors.TIMELINE_CLIP_FIRST)
        device.tap(Anchors.TIMELINE_SPLIT)
        device.tap(Anchors.TIMELINE_TRACK_MENU_FIRST)
        device.tap(Anchors.TIMELINE_TRACK_SETTINGS)
        device.tap(Anchors.TIMELINE_SETTINGS_CLOSE)
        device.anchor(Anchors.TIMELINE_ROOT)
        device.tap(Anchors.TRANSPORT_METRONOME)
        device.tap(Anchors.TRANSPORT_METRONOME)
        device.tap(Anchors.TRANSPORT_PLAY)
        device.tap(Anchors.TRANSPORT_SKIP_START)
    }

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val PINCH_PERCENT = 0.6f
        const val PINCH_SPEED = 400
    }
}
