package audio.tembra.perf.journeys

import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap
import audio.tembra.perf.journeys.Anchors.tapIfPresent

/**
 * The stress journey (spec FR-007, placeholder until the audio spine): the metronome keeps clicking while the tuner
 * listens, then the metronome stops; the last tap, the "write engine health" control of the measurement build,
 * dumps the audio-health counters (feature 007, US5).
 */
class StressJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_METRONOME)
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
        // Last step: the measurement build dumps the audio-health counters (feature 007, US5); the release
        // baseline (-Ptembra.perf=release) has no devtools, so the control is tapped only when it exists.
        device.tapIfPresent(Anchors.DEVTOOLS_WRITE_HEALTH)
    }
}
