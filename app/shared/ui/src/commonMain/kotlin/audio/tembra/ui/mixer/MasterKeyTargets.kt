package audio.tembra.ui.mixer

import audio.tembra.ui.designsystem.FaderScale
import audio.tembra.ui.shortcuts.MixerShortcuts
import audio.tembra.ui.timeline.TimelineCallbacks
import kotlin.math.roundToInt

/** The master strip's answers to the mixer's keys (feature 012, spec FR-028): its percent fader alone; no pan, no toggles. */
internal class MasterKeyTargets(
    private val master: MasterUi,
    private val callbacks: TimelineCallbacks,
) : MixerShortcuts {
    override fun faderStep(direction: Int): Boolean = volumeTo(master.percent + direction * FaderScale.Percent.step.roundToInt())

    override fun knobStep(direction: Int): Boolean = false

    override fun page(direction: Int): Boolean = volumeTo(master.percent + direction * FaderScale.Percent.page.roundToInt())

    override fun home(): Boolean = volumeTo(FaderScale.Percent.HOME_PERCENT)

    override fun mute(): Boolean = false

    override fun solo(): Boolean = false

    private fun volumeTo(percent: Int): Boolean {
        val next = percent.coerceIn(0, FaderScale.Percent.range.endInclusive.roundToInt())
        if (next != master.percent) callbacks.onMasterVolume(next)
        callbacks.onMasterSettled()
        return true
    }
}
