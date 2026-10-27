package audio.tembra.ui.session

import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TickGrid
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.engine.ClickSoundId
import audio.tembra.engine.MetronomeAudio
import audio.tembra.engine.MetronomeGrid
import audio.tembra.engine.TimelineAudio

/**
 * The transport's click (spec FR-036 to FR-040; research R4; the constitution's reuse rule): the grid is the global
 * metronome's bar shape (its subdivision, accent pattern and style from [MetronomeSettings]) laid over the project's
 * signature at the project's tempo, published through the metronome's own `setGrid` and `selectSound`; the toggle
 * goes through `TimelineAudio.setMetronome`. The standalone Metronome destination keeps its own tempo untouched.
 */
class MetronomeBridge(private val metronome: MetronomeAudio, private val transport: TimelineAudio) {

    /** The grid the engine plays for [settings] at the project's [tempo] and [signature]. */
    fun gridFor(settings: MetronomeSettings, tempo: Tempo, signature: TimeSignature): MetronomeGrid {
        val pattern = settings.style?.pattern(signature) ?: settings.pattern.resizedTo(signature.beats)
        val tickGrid = TickGrid.from(signature, settings.subdivision, pattern, settings.style)
        return MetronomeGrid.from(tickGrid, tempo)
    }

    /** Pushes the grid and the sound; the engine applies both from its next block. */
    fun apply(settings: MetronomeSettings, tempo: Tempo, signature: TimeSignature) {
        metronome.setGrid(gridFor(settings, tempo, signature))
        metronome.selectSound(soundIdOf(settings.sound))
    }

    fun setEnabled(enabled: Boolean) {
        transport.setMetronome(enabled)
    }

    private fun soundIdOf(sound: ClickSound): ClickSoundId = when (sound) {
        is ClickSound.BuiltIn -> when (sound.sound) {
            BuiltInClickSound.ClassicWood -> ClickSoundId.ClassicWood
            BuiltInClickSound.Rim808 -> ClickSoundId.Rim808
            BuiltInClickSound.DigitalBeep -> ClickSoundId.DigitalBeep
            BuiltInClickSound.Cowbell -> ClickSoundId.Cowbell
            BuiltInClickSound.Mechanical -> ClickSoundId.Mechanical
        }
        is ClickSound.Custom -> ClickSoundId.Custom
    }
}
