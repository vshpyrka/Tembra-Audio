package audio.tembra.ui.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode

/** The intents the stateless screen raises; the stateful [TunerScreen] binds them to the ViewModel. */
data class TunerCallbacks(
    val onInstrument: (Instrument) -> Unit,
    val onGuitarStrings: (GuitarStringCount) -> Unit,
    val onPreset: (String) -> Unit,
    val onMode: (TunerMode) -> Unit,
    val onLoopTone: (Boolean) -> Unit,
    val onAudition: (Int) -> Unit,
    val onStopAudition: () -> Unit,
    val onRequestMicrophone: () -> Unit,
) {
    companion object {
        /** No-op callbacks for previews and tests. */
        val None = TunerCallbacks({}, {}, {}, {}, {}, {}, {}, {})
    }
}
