package audio.tembra.perf.journeys

import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/**
 * The stress journey (spec FR-007, placeholder until the audio spine): the metronome keeps clicking while the tuner
 * listens, then the metronome stops. Its last step, a tap on the devtools "write engine health" action that dumps
 * the audio-health counters (feature 007, US5), is appended with that action.
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
    }
}
