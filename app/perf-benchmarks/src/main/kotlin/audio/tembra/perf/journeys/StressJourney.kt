package audio.tembra.perf.journeys

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap
import audio.tembra.perf.journeys.Anchors.tapIfPresent

/**
 * The stress journey (spec FR-007; extended by feature 009): the demo project plays with the click on while the lane
 * scrolls for five seconds, the tuner cycle then takes the audio (leaving the Timeline through the stop-and-leave
 * dialog), the metronome keeps clicking while the tuner listens, then the metronome stops; the last tap, the "write
 * engine health" control of the measurement build, dumps the audio-health counters (feature 007, US5). Feature 012
 * runs it on the 24-track stress project when the launch built one (`tembra.stress.tracks=24`, the demo's loops
 * imported six times over) and adds the mixer phase before the health dump.
 */
class StressJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_PROJECTS)
        device.anchor(Anchors.PROJECTS_ROOT)
        // The stress project's card when the launch built it, the demo's otherwise (a release baseline has no launch argument).
        val stress = device.wait(Until.hasObject(By.res(Anchors.PROJECTS_CARD_STRESS)), STRESS_CARD_TIMEOUT_MS)
        device.tap(if (stress) Anchors.PROJECTS_CARD_STRESS else Anchors.PROJECTS_CARD_DEMO)
        device.anchor(Anchors.TIMELINE_ROOT)
        device.tap(Anchors.TRANSPORT_METRONOME)
        device.tap(Anchors.TRANSPORT_PLAY)
        scrollLane(device, Anchors.TIMELINE_LANE_FIRST, SCROLL_MILLIS)
        recordWhileScrolling(device, newTrack = if (stress) STRESS_TRACK_COUNT else RecordingJourney.NEW_TRACK_INDEX)
        mixerPhase(device)
        // The measurement build dumps the audio-health counters here, while the transport still owns the device (the
        // engine restarts them per stream session and the metronome and the tuner take the device next; feature 007,
        // US5); the release baseline (-Ptembra.perf=release) has no devtools, so the control is tapped only when it exists.
        device.tapIfPresent(Anchors.DEVTOOLS_WRITE_HEALTH)
        device.tap(Anchors.NAV_METRONOME)
        // Leaving the Timeline while it plays: the stop-and-leave dialog (spec FR-008a of feature 009).
        device.tap(Anchors.DIALOG_STOP_AND_LEAVE_CONFIRM)
        device.anchor(Anchors.METRONOME_ROOT)
        device.tap(Anchors.METRONOME_START_STOP)
        device.tap(Anchors.NAV_TUNER)
        device.anchor(Anchors.TUNER_NOTE_READOUT)
        repeat(3) {
            device.tap(Anchors.TUNER_MODE_BY_EAR)
            device.tap(Anchors.TUNER_MODE_AUTO_LISTEN)
        }
        device.anchor(Anchors.TUNER_NOTE_READOUT)
        device.tap(Anchors.NAV_METRONOME)
        device.tap(Anchors.METRONOME_START_STOP)
    }

    /**
     * Feature 010: add a track, arm it, record for three seconds while the lane scrolls, stop the take, undo twice (the
     * take, then the track). Guarded: a device without the microphone grant finds no meter, skips the recording and
     * the journey completes (the tap of Record is then a no-op). The new track's lane is the one scrolled, as on iOS:
     * the list scrolls the added track into view, and on the 24-track project the first lane leaves the lazy list's
     * composition (a handle taken before the add throws `StaleObjectException`, a fresh lookup finds nothing; the
     * 2026-11-09 rounds).
     */
    private fun recordWhileScrolling(device: UiDevice, newTrack: Int) {
        val index = newTrack
        device.tapIfPresent(Anchors.TIMELINE_ADD_TRACK)
        device.tapIfPresent(Anchors.trackArm(index))
        if (device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.res(Anchors.trackMeter(index))), ARM_TIMEOUT_MS)) {
            device.tapRecord(index)
            scrollLane(device, Anchors.timelineLane(index), RECORD_MILLIS)
            device.tapRecord(index)
            device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res(Anchors.TIMELINE_CLIP_TAKE)), ADOPT_TIMEOUT_MS)
            device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(Anchors.TIMELINE_UNDO).enabled(true)), ADOPT_TIMEOUT_MS)
            device.tapIfPresent(Anchors.trackArm(index))
            device.tapIfPresent(Anchors.TIMELINE_UNDO)
        }
        device.tapIfPresent(Anchors.TIMELINE_UNDO)
    }

    /**
     * Feature 012 (contracts/perf-instrumentation.md "Journey `stress`"): the mixer open for five seconds with the meters
     * live while the first lane scrolls above the half sheet and the metronome runs, one drag of the first fader (undone:
     * the document persists between iterations), then closed. The Mixer control sits in the toolbar on a wide bar and in
     * the overflow menu on a compact one. The sheet's meters keep the UI from reading idle while it is open, hence the
     * short idle waits (`withShortIdleWaits`). The recording phase leaves the stress project's list scrolled to where
     * its new track was, so the list is scrolled back to its top first: the first lane then stays visible above the
     * half sheet.
     */
    private fun mixerPhase(device: UiDevice): Unit = withShortIdleWaits {
        scrollListToTop(device)
        toggleMixer(device)
        device.anchor(Anchors.MIXER_SHEET)
        scrollLane(device, Anchors.TIMELINE_LANE_FIRST, MIXER_MILLIS)
        device.anchor(Anchors.MIXER_STRIP_FADER_FIRST).swipe(Direction.UP, FADER_SWIPE_PERCENT, SCROLL_SPEED)
        device.waitForIdle()
        device.tapIfPresent(Anchors.TIMELINE_UNDO)
        toggleMixer(device)
        device.wait(Until.gone(By.res(Anchors.MIXER_SHEET)), ADOPT_TIMEOUT_MS)
    }

    /**
     * Scrolls the lane [tag] right and left until [millis] have passed. The lane's node can be replaced while the list
     * recomposes (a track added or armed, the sheet opening), which makes a held `UiObject2` stale; the lane is then
     * found again and the scrolling goes on.
     */
    private fun scrollLane(device: UiDevice, tag: String, millis: Long) {
        var lane: UiObject2 = device.anchor(tag)
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            try {
                lane.scroll(Direction.RIGHT, SCROLL_PERCENT, SCROLL_SPEED)
                lane.scroll(Direction.LEFT, SCROLL_PERCENT, SCROLL_SPEED)
            } catch (_: StaleObjectException) {
                lane = device.anchor(tag)
            }
        }
    }

    /**
     * Swipes down over the track list, fast and across most of its height, until the first lane is composed again: a
     * `UiObject2.fling` on one lane travels a lane's height and barely moves a 25-track list, where three such swipes
     * bring lane 0 back from the end (measured by hand on the Pixel 6 Pro, 2026-11-09).
     */
    private fun scrollListToTop(device: UiDevice) {
        repeat(MAX_LIST_SCROLLS) {
            if (device.hasObject(By.res(Anchors.TIMELINE_LANE_FIRST))) return
            val bounds = (device.findObject(By.res(Anchors.TIMELINE_TRACKS)) ?: return).visibleBounds
            val x = bounds.centerX()
            device.swipe(x, bounds.top + bounds.height() / 4, x, bounds.bottom - bounds.height() / 8, LIST_SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    private fun toggleMixer(device: UiDevice) {
        if (device.hasObject(By.res(Anchors.TIMELINE_MIXER))) {
            device.tap(Anchors.TIMELINE_MIXER)
        } else {
            device.tap(Anchors.TIMELINE_OVERFLOW_BUTTON)
            device.tap(Anchors.TIMELINE_OVERFLOW_MIXER)
        }
    }

    private companion object {
        /** The stress project's tracks: the launch argument of `StressJourneyBenchmark`, the added track's index. */
        const val STRESS_TRACK_COUNT = 24
        const val STRESS_CARD_TIMEOUT_MS = 5_000L
        const val MIXER_MILLIS = 5_000L
        const val FADER_SWIPE_PERCENT = 0.3f
        const val RECORD_MILLIS = 3_000L
        const val ARM_TIMEOUT_MS = 3_000L
        const val ADOPT_TIMEOUT_MS = 10_000L
        const val SCROLL_MILLIS = 5_000L
        const val SCROLL_PERCENT = 0.5f
        const val SCROLL_SPEED = 2_000
        /** Downward swipes that bring a 25-track list back to its top (three suffice on the Pixel 6 Pro). */
        const val MAX_LIST_SCROLLS = 10
        /** Steps of one list swipe, about 5 ms each: fast enough to fling. */
        const val LIST_SWIPE_STEPS = 12
    }
}
