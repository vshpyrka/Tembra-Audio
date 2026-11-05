package audio.tembra.ui.mixer

import audio.tembra.ui.perf.PerfAnchors

/**
 * Every `testTag` of the mixer (feature 012, contracts/mixer-ui.md "Semantics and anchors"), dotted like
 * `TimelineTestTags`; the journey anchors come from [PerfAnchors] and the per-strip tags carry the strip's index
 * (`mixer.strip.fader.0` is the first strip's fader, the anchor the journeys drive).
 */
object MixerTestTags {
    const val TOOLBAR_BUTTON = PerfAnchors.TIMELINE_MIXER
    const val OVERFLOW_BUTTON = PerfAnchors.TIMELINE_OVERFLOW_MIXER
    const val SHEET = PerfAnchors.MIXER_SHEET
    const val HANDLE = PerfAnchors.MIXER_HANDLE
    const val STRIPS = PerfAnchors.MIXER_STRIPS
    const val MASTER = "mixer.master"
    const val MASTER_FADER = PerfAnchors.MIXER_MASTER_FADER
    const val MASTER_METER = PerfAnchors.MIXER_MASTER_METER
    const val MASTER_CLIP = PerfAnchors.MIXER_MASTER_CLIP
    const val WINDOW_OPEN = PerfAnchors.MIXER_WINDOW_OPEN

    fun strip(index: Int): String = "mixer.strip.$index"
    fun number(index: Int): String = "mixer.strip.number.$index"
    fun fader(index: Int): String = "mixer.strip.fader.$index"
    fun pan(index: Int): String = "mixer.strip.pan.$index"
    fun solo(index: Int): String = "mixer.strip.solo.$index"
    fun mute(index: Int): String = "mixer.strip.mute.$index"
    fun meter(index: Int): String = "mixer.strip.meter.$index"
    fun vu(index: Int): String = "mixer.strip.vu.$index"
    fun clip(index: Int): String = "mixer.strip.clip.$index"
    fun name(index: Int): String = "mixer.strip.name.$index"
}
