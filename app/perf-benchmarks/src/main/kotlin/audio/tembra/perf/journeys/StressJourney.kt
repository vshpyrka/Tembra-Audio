package audio.tembra.perf.journeys

import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap
import audio.tembra.perf.journeys.Anchors.tapIfPresent

/**
 * The stress journey (spec FR-007; extended by feature 009): the demo project plays with the click on while the lane
 * scrolls for five seconds, the tuner cycle then takes the audio (leaving the Timeline through the stop-and-leave
 * dialog), the metronome keeps clicking while the tuner listens, then the metronome stops; the last tap, the "write
 * engine health" control of the measurement build, dumps the audio-health counters (feature 007, US5).
 */
class StressJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_PROJECTS)
        device.anchor(Anchors.PROJECTS_ROOT)
        device.tap(Anchors.PROJECTS_CARD_DEMO)
        device.anchor(Anchors.TIMELINE_ROOT)
        device.tap(Anchors.TRANSPORT_METRONOME)
        device.tap(Anchors.TRANSPORT_PLAY)
        val lane = device.anchor(Anchors.TIMELINE_LANE_FIRST)
        val until = System.currentTimeMillis() + SCROLL_MILLIS
        while (System.currentTimeMillis() < until) {
            lane.scroll(androidx.test.uiautomator.Direction.RIGHT, SCROLL_PERCENT, SCROLL_SPEED)
            lane.scroll(androidx.test.uiautomator.Direction.LEFT, SCROLL_PERCENT, SCROLL_SPEED)
        }
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

    private companion object {
        const val SCROLL_MILLIS = 5_000L
        const val SCROLL_PERCENT = 0.5f
        const val SCROLL_SPEED = 2_000
    }
}
