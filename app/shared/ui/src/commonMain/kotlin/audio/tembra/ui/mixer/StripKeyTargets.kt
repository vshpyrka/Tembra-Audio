package audio.tembra.ui.mixer

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.ui.designsystem.FaderScale
import audio.tembra.ui.shortcuts.MixerShortcuts
import audio.tembra.ui.timeline.TimelineCallbacks

/**
 * One track strip's answers to the mixer's keys (feature 012, spec FR-028): the fader by the scale's step or page and
 * home, the pan by a percent, the toggles; each a settled gesture of its own (FR-005). Built per strip from its state
 * and the timeline's callbacks; the strip's own fader and knob consume their keys before this sees them.
 */
internal class StripKeyTargets(
    private val strip: StripUi,
    private val callbacks: TimelineCallbacks,
) : MixerShortcuts {
    override fun faderStep(direction: Int): Boolean = gainTo(FaderScale.Gain.valueOf(strip.gainDb) + direction * FaderScale.Gain.step)

    override fun knobStep(direction: Int): Boolean {
        callbacks.onPan(strip.id, Pan.clamped(strip.pan.percent + direction))
        callbacks.onPanSettled()
        return true
    }

    override fun page(direction: Int): Boolean = gainTo(FaderScale.Gain.valueOf(strip.gainDb) + direction * FaderScale.Gain.page)

    override fun home(): Boolean = gainTo(FaderScale.Gain.unity)

    override fun mute(): Boolean = true.also { callbacks.onToggleMute(strip.id) }

    override fun solo(): Boolean = true.also { callbacks.onToggleSolo(strip.id) }

    private fun gainTo(value: Float): Boolean {
        val next: GainDb = FaderScale.Gain.gainOf(FaderScale.Gain.quantize(value.coerceIn(FaderScale.Gain.range)))
        if (next != strip.gainDb) callbacks.onGain(strip.id, next)
        callbacks.onGainSettled()
        return true
    }
}
