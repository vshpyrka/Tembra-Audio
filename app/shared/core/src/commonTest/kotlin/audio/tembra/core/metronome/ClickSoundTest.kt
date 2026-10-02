package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sound list order and the remembered settings defaults (spec FR-018, FR-021, FR-026). */
class ClickSoundTest {

    @Test
    fun builtInSoundsAreInSpecOrder() {
        assertEquals(
            listOf(
                BuiltInClickSound.ClassicWood,
                BuiltInClickSound.Rim808,
                BuiltInClickSound.DigitalBeep,
                BuiltInClickSound.Cowbell,
                BuiltInClickSound.Mechanical,
            ),
            BuiltInClickSound.entries,
        )
        assertEquals(
            listOf("classic-wood", "808-rim", "digital-beep", "cowbell", "mechanical"),
            BuiltInClickSound.entries.map { it.id },
        )
        assertEquals(
            listOf("Classic Wood", "808 Rim", "Digital Beep", "Cowbell", "Mechanical"),
            BuiltInClickSound.entries.map { it.label },
        )
    }

    @Test
    fun customSoundShowsItsFileName() {
        assertEquals("Kick", ClickSound.Custom("Kick").displayName)
    }

    @Test
    fun settingsDefaults() {
        val settings = MetronomeSettings()
        assertEquals(Tempo(120), settings.tempo)
        assertEquals(TimeSignature.FourFour, settings.signature)
        assertEquals(Subdivision.One, settings.subdivision)
        assertEquals("ACCC", settings.pattern.encode())
        assertEquals(MetronomeStyle.Standard, settings.style)
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood), settings.sound)
        assertNull(settings.customSound)
    }

    @Test
    fun customSoundRefKeepsItsPath() {
        assertEquals("/p", CustomSoundRef("Kick", "/p").path)
    }
}
