package audio.tembra.ui.metronome

import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.SyncopationReading
import audio.tembra.core.metronome.TapTempoSequence
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.core.metronome.TickGrid
import audio.tembra.core.metronome.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The screen state and its derived values (data-model.md §8). */
class MetronomeUiStateTest {

    @Test
    fun defaults() {
        val state = MetronomeUiState()
        assertFalse(state.loaded)
        assertEquals(MetronomeSettings(), state.settings)
        assertEquals(PlaybackState.Stopped, state.playback)
        assertEquals(-1, state.currentBeat)
        assertEquals(TapTempoSequence(), state.tap)
        assertEquals(TapCaption.Rest, state.tapCaption)
        assertEquals(CustomSoundState.None, state.customSound)
        assertEquals(SyncopationReading.EvenBeats, state.syncopationReading)
    }

    @Test
    fun markingFollowsTheTempo() {
        assertEquals(TempoMarking.Moderato, MetronomeUiState().marking)
        val faster = MetronomeUiState(settings = MetronomeSettings(tempo = Tempo(121)))
        assertEquals(TempoMarking.Allegro, faster.marking)
    }

    @Test
    fun subdivisionLabelsFollowTheSignaturesUnit() {
        assertEquals(listOf("1/4", "1/8", "1/16", "3let"), MetronomeUiState().subdivisionLabels)
        val eighths = MetronomeUiState(settings = MetronomeSettings(signature = TimeSignature.SevenEight))
        assertEquals(listOf("1/8", "1/16", "1/32", "3let"), eighths.subdivisionLabels)
    }

    @Test
    fun gridIsTheTickGridOfTheSettingsAndReading() {
        val settings = MetronomeSettings(
            signature = TimeSignature.FourFour,
            subdivision = Subdivision.Two,
            pattern = MetronomeStyle.Syncopated.pattern(TimeSignature.FourFour),
            style = MetronomeStyle.Syncopated,
        )
        val state = MetronomeUiState(settings = settings, syncopationReading = SyncopationReading.OffBeatTicks)
        assertEquals(
            TickGrid.from(settings.signature, settings.subdivision, settings.pattern, settings.style, SyncopationReading.OffBeatTicks),
            state.grid,
        )
        assertEquals(TickGrid.from(TimeSignature.FourFour, Subdivision.One, MetronomeSettings().pattern, MetronomeStyle.Standard), MetronomeUiState().grid)
    }

    @Test
    fun stepLimitsAtTheEndsOfTheRange() {
        val slowest = MetronomeUiState(settings = MetronomeSettings(tempo = Tempo(40)))
        assertFalse(slowest.canStepDown)
        assertTrue(slowest.canStepUp)
        val fastest = MetronomeUiState(settings = MetronomeSettings(tempo = Tempo(240)))
        assertTrue(fastest.canStepDown)
        assertFalse(fastest.canStepUp)
        assertTrue(MetronomeUiState().canStepDown && MetronomeUiState().canStepUp)
    }

    @Test
    fun runningOnlyWhileRunning() {
        assertFalse(MetronomeUiState(playback = PlaybackState.Stopped).running)
        assertFalse(MetronomeUiState(playback = PlaybackState.Starting).running)
        assertTrue(MetronomeUiState(playback = PlaybackState.Running).running)
    }
}
