package audio.tembra.ui.metronome

import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature

/** The intents the stateless screen raises; the stateful [MetronomeScreen] binds them to the ViewModel. */
data class MetronomeCallbacks(
    val onTempo: (Tempo) -> Unit,
    val onStep: (Int) -> Unit,
    val onSignature: (TimeSignature) -> Unit,
    val onSubdivision: (Subdivision) -> Unit,
    /** The 0-based beat whose level cycles. */
    val onCycleBeat: (Int) -> Unit,
    val onStyle: (MetronomeStyle) -> Unit,
    val onSound: (ClickSound) -> Unit,
    val onImportCustom: () -> Unit,
    val onReplaceCustom: () -> Unit,
    /** The frame time of the tap, in nanoseconds. */
    val onTap: (Long) -> Unit,
    val onTogglePlayback: () -> Unit,
) {
    companion object {
        /** No-op callbacks for previews and tests. */
        val None = MetronomeCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
