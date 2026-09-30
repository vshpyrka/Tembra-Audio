package audio.tembra.ui.metronome

import audio.tembra.core.metronome.SyncopationReading

/**
 * The metronome's debug launch argument (data-model.md §10, research R14): how the Syncopated style is rendered, so
 * the readings of spec FR-037 can be auditioned. Only debug builds parse it (Android extra `tembra.metronome.syncopation`,
 * iOS argument `--tembra-metronome-syncopation=`); it reaches the screen through [LocalMetronomeDebug].
 */
data class MetronomeDebugRequest(val syncopation: SyncopationReading) {
    companion object {
        /** `"even-beats"`, `"off-beat-ticks"` or `"all-but-one"`; any other value, or none, is ignored. */
        fun parse(value: String?): MetronomeDebugRequest? =
            SyncopationReading.entries.firstOrNull { it.id == value }?.let(::MetronomeDebugRequest)
    }
}
