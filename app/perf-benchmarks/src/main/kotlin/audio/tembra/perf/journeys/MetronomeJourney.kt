package audio.tembra.perf.journeys

import androidx.test.uiautomator.UiDevice
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/** The metronome journey (spec FR-007): open Metronome, start, tempo up x5, tempo down x5, stop. */
class MetronomeJourney {
    fun run(device: UiDevice) {
        device.tap(Anchors.NAV_METRONOME)
        device.anchor(Anchors.METRONOME_ROOT)
        device.anchor(Anchors.METRONOME_TEMPO_READOUT)
        device.tap(Anchors.METRONOME_START_STOP)
        repeat(5) { device.tap(Anchors.METRONOME_TEMPO_UP) }
        repeat(5) { device.tap(Anchors.METRONOME_TEMPO_DOWN) }
        device.tap(Anchors.METRONOME_START_STOP)
    }
}
