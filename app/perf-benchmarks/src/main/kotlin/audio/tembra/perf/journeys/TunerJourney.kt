package audio.tembra.perf.journeys

import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/** The tuner journey (spec FR-007): open Tuner, wait for the note readout, switch to By Ear, back to Auto Listen. */
class TunerJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_TUNER)
        device.anchor(Anchors.TUNER_ROOT)
        device.anchor(Anchors.TUNER_NOTE_READOUT)
        device.tap(Anchors.TUNER_MODE_BY_EAR)
        device.tap(Anchors.TUNER_MODE_AUTO_LISTEN)
        device.anchor(Anchors.TUNER_NOTE_READOUT)
    }
}
