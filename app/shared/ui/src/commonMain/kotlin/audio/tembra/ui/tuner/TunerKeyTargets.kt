package audio.tembra.ui.tuner

import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.shortcuts.TunerShortcuts

/** The Tuner's shortcut actions against the state of the moment. */
internal class TunerKeyTargets(private val state: TunerUiState, private val callbacks: TunerCallbacks) : TunerShortcuts {
    private val byEar get() = state.mode == TunerMode.ByEar

    override fun autoListen(): Boolean = true.also { callbacks.onMode(TunerMode.AutoListen) }

    override fun byEar(): Boolean = true.also { callbacks.onMode(TunerMode.ByEar) }

    override fun playString(number: Int): Boolean {
        val strings = state.strings
        if (!byEar || number !in 1..strings.size) return false
        // The cards run from the lowest string (the highest number) to string I.
        callbacks.onAudition(strings.size - number)
        return true
    }

    override fun stopTone(): Boolean = byEar.also { if (it) callbacks.onStopAudition() }

    override fun toggleLoop(): Boolean = byEar.also { if (it) callbacks.onLoopTone(!state.loopTone) }
}
