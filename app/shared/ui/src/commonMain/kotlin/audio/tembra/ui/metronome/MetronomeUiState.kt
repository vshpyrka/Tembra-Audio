package audio.tembra.ui.metronome

import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.SyncopationReading
import audio.tembra.core.metronome.TapTempoSequence
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.core.metronome.TickGrid

/**
 * Everything the metronome screen renders (data-model.md §8). [currentBeat] is the ViewModel's 10 Hz sample for
 * semantics and tests; the beat light itself is read in the draw phase through [BeatTelemetry] (research R7).
 */
data class MetronomeUiState(
    /** The stored settings have been read; the controls are inert before. */
    val loaded: Boolean = false,
    val settings: MetronomeSettings = MetronomeSettings(),
    val playback: PlaybackState = PlaybackState.Stopped,
    /** The 0-based beat the engine last played while running, else -1. */
    val currentBeat: Int = -1,
    val tap: TapTempoSequence = TapTempoSequence(),
    val tapCaption: TapCaption = TapCaption.Rest,
    val customSound: CustomSoundState = CustomSoundState.None,
    /** Debug builds only (research R14). */
    val syncopationReading: SyncopationReading = SyncopationReading.EvenBeats,
) {
    /** The first content is on screen: what `ReportFirstContentDrawn` reports (feature 007). */
    val isFirstContentReady: Boolean get() = loaded

    val marking: TempoMarking get() = TempoMarking.of(settings.tempo)

    /** The subdivision chips' labels in display order, for the signature's beat unit. */
    val subdivisionLabels: List<String> get() = Subdivision.entries.map { it.label(settings.signature.unit) }

    /** The bar as the engine plays it. */
    val grid: TickGrid
        get() = TickGrid.from(settings.signature, settings.subdivision, settings.pattern, settings.style, syncopationReading)

    val running: Boolean get() = playback == PlaybackState.Running
    val canStepDown: Boolean get() = settings.tempo.bpm > Tempo.MIN
    val canStepUp: Boolean get() = settings.tempo.bpm < Tempo.MAX
}
