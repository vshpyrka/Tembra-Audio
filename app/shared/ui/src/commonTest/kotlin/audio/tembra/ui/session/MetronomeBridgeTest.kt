package audio.tembra.ui.session

import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.engine.ClickLevel
import audio.tembra.engine.FakeAudioEngine
import kotlin.test.Test
import kotlin.test.assertEquals

/** The transport's click reuses the global metronome's bar shape at the project tempo (spec FR-036–FR-040; T039). */
class MetronomeBridgeTest {

    private val engine = FakeAudioEngine()
    private val bridge = MetronomeBridge(engine, engine)

    @Test
    fun theGridComesFromTheGlobalSettingsAndTheProjectTempoAndSignature() {
        val settings = MetronomeSettings(tempo = Tempo(90), subdivision = Subdivision.Default, style = MetronomeStyle.Standard)
        val grid = bridge.gridFor(settings, Tempo(124), TimeSignature.SixEight)
        assertEquals(Tempo(124), grid.tempo, "the project tempo, never the metronome's own")
        assertEquals(6, grid.levels.size / grid.ticksPerBeat)
        assertEquals(ClickLevel.Accent, grid.levels.first())
    }

    @Test
    fun applyPushesTheGridAndTheSoundThroughTheMetronomeSeam() {
        val settings = MetronomeSettings(sound = ClickSound.BuiltIn(BuiltInClickSound.Cowbell))
        bridge.apply(settings, Tempo(124), TimeSignature.FourFour)
        assertEquals(listOf("setGrid(bpm=124, ticks=1, levels=ACCC)", "selectSound(Cowbell)"), engine.calls)
    }

    @Test
    fun theToggleGoesThroughTheTransport() {
        bridge.setEnabled(true)
        bridge.setEnabled(false)
        assertEquals(listOf("setMetronome(true)", "setMetronome(false)"), engine.calls)
    }
}
