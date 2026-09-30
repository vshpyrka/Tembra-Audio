package audio.tembra.data.metronome

import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.CustomSoundRef
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Encoding, decoding rules and the round trip of the remembered metronome state (contracts/metronome-preferences.md). */
class MetronomePreferencesSnapshotTest {

    private fun decode(vararg pairs: Pair<String, String?>) = MetronomePreferencesSnapshot.decode(mapOf(*pairs))

    @Test
    fun defaultsEqualTheDefaultSettings() {
        assertEquals(MetronomeSettings(), MetronomePreferencesSnapshot().settings)
        assertEquals(MetronomePreferencesSnapshot(), decode())
    }

    @Test
    fun encodeYieldsTheKeysOfTheDefaults() {
        val encoded = MetronomePreferencesSnapshot().encode()
        assertEquals(
            mapOf(
                MetronomePreferenceKeys.TEMPO to "120",
                MetronomePreferenceKeys.SIGNATURE to "4-4",
                MetronomePreferenceKeys.SUBDIVISION to "1",
                MetronomePreferenceKeys.LEVELS to "ACCC",
                MetronomePreferenceKeys.STYLE to "standard",
                MetronomePreferenceKeys.SOUND to "classic-wood",
            ),
            encoded,
        )
        assertEquals("metronome.tempo", MetronomePreferenceKeys.TEMPO)
        assertEquals("metronome.signature", MetronomePreferenceKeys.SIGNATURE)
        assertEquals("metronome.subdivision", MetronomePreferenceKeys.SUBDIVISION)
        assertEquals("metronome.levels", MetronomePreferenceKeys.LEVELS)
        assertEquals("metronome.style", MetronomePreferenceKeys.STYLE)
        assertEquals("metronome.sound", MetronomePreferenceKeys.SOUND)
        assertEquals("metronome.customName", MetronomePreferenceKeys.CUSTOM_NAME)
        assertEquals("metronome.customPath", MetronomePreferenceKeys.CUSTOM_PATH)
        assertNull(encoded[MetronomePreferenceKeys.CUSTOM_NAME])
        assertNull(encoded[MetronomePreferenceKeys.CUSTOM_PATH])
    }

    @Test
    fun aTempoThatIsNotAnIntegerInRangeDecodesToTheDefault() {
        assertEquals(Tempo(120), decode(MetronomePreferenceKeys.TEMPO to "abc").settings.tempo)
        assertEquals(Tempo(120), decode(MetronomePreferenceKeys.TEMPO to "300").settings.tempo)
        assertEquals(Tempo(120), decode(MetronomePreferenceKeys.TEMPO to "39").settings.tempo)
        assertEquals(Tempo(90), decode(MetronomePreferenceKeys.TEMPO to "90").settings.tempo)
    }

    @Test
    fun unknownIdsDecodeToTheDefaults() {
        val settings = decode(
            MetronomePreferenceKeys.SIGNATURE to "9-8",
            MetronomePreferenceKeys.SUBDIVISION to "7",
            MetronomePreferenceKeys.STYLE to "jazz",
            MetronomePreferenceKeys.SOUND to "kazoo",
        ).settings
        assertEquals(TimeSignature.FourFour, settings.signature)
        assertEquals(Subdivision.One, settings.subdivision)
        assertEquals(MetronomeStyle.Standard, settings.style)
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood), settings.sound)
    }

    @Test
    fun levelsOfTheWrongLengthAreRefittedToTheSignature() {
        val settings = decode(
            MetronomePreferenceKeys.SIGNATURE to "4-4",
            MetronomePreferenceKeys.LEVELS to "AC",
        ).settings
        assertEquals("ACCC", settings.pattern.encode())
    }

    @Test
    fun levelsWithABadCharacterFallBackToTheStylePatternOrStandard() {
        val withStyle = decode(
            MetronomePreferenceKeys.SIGNATURE to "4-4",
            MetronomePreferenceKeys.STYLE to "downbeat",
            MetronomePreferenceKeys.LEVELS to "AXCC",
        ).settings
        assertEquals(MetronomeStyle.Downbeat.pattern(TimeSignature.FourFour), withStyle.pattern)
        val withoutStyle = decode(
            MetronomePreferenceKeys.SIGNATURE to "3-4",
            MetronomePreferenceKeys.LEVELS to "AXC",
        ).settings
        assertEquals(MetronomeStyle.Standard.pattern(TimeSignature.ThreeFour), withoutStyle.pattern)
    }

    @Test
    fun absentLevelsUseTheStylePatternForTheSignature() {
        val settings = decode(
            MetronomePreferenceKeys.SIGNATURE to "6-8",
            MetronomePreferenceKeys.STYLE to "standard",
        ).settings
        assertEquals(MetronomeStyle.Standard.pattern(TimeSignature.SixEight), settings.pattern)
    }

    @Test
    fun anAbsentStyleIsDerivedFromTheLevels() {
        assertEquals(MetronomeStyle.Standard, decode(MetronomePreferenceKeys.LEVELS to "ACCC").settings.style)
        assertEquals(MetronomeStyle.Downbeat, decode(MetronomePreferenceKeys.LEVELS to "ASSS").settings.style)
        assertNull(decode(MetronomePreferenceKeys.LEVELS to "SSSS").settings.style)
    }

    @Test
    fun customSoundWithoutAPathDecodesToClassicWoodAndNoReference() {
        val settings = decode(MetronomePreferenceKeys.SOUND to "custom").settings
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood), settings.sound)
        assertNull(settings.customSound)
        val nameOnly = decode(
            MetronomePreferenceKeys.SOUND to "custom",
            MetronomePreferenceKeys.CUSTOM_NAME to "clap",
        ).settings
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood), nameOnly.sound)
        assertNull(nameOnly.customSound)
    }

    @Test
    fun bothCustomKeysDecodeToTheReferenceEvenWhileABuiltInSoundIsSelected() {
        val settings = decode(
            MetronomePreferenceKeys.SOUND to "cowbell",
            MetronomePreferenceKeys.CUSTOM_NAME to "clap",
            MetronomePreferenceKeys.CUSTOM_PATH to "/docs/metronome/custom.wav",
        ).settings
        assertEquals(CustomSoundRef("clap", "/docs/metronome/custom.wav"), settings.customSound)
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.Cowbell), settings.sound)
        val selected = decode(
            MetronomePreferenceKeys.SOUND to "custom",
            MetronomePreferenceKeys.CUSTOM_NAME to "clap",
            MetronomePreferenceKeys.CUSTOM_PATH to "/docs/metronome/custom.wav",
        ).settings
        assertEquals(ClickSound.Custom("clap"), selected.sound)
    }

    @Test
    fun aFullRoundTripIsTheIdentity() {
        val snapshot = MetronomePreferencesSnapshot(
            MetronomeSettings(
                tempo = Tempo(87),
                signature = TimeSignature.SevenEight,
                subdivision = Subdivision.Triplet,
                pattern = MetronomeStyle.Syncopated.pattern(TimeSignature.SevenEight),
                style = MetronomeStyle.Syncopated,
                sound = ClickSound.Custom("clap"),
                customSound = CustomSoundRef("clap", "/docs/metronome/custom.wav"),
            ),
        )
        assertEquals(snapshot, MetronomePreferencesSnapshot.decode(snapshot.encode()))
        val handEdited = MetronomePreferencesSnapshot(
            MetronomeSettings(
                signature = TimeSignature.FourFour,
                pattern = AccentPattern.decode("SSSA", 4)!!,
                style = null,
            ),
        )
        assertEquals(handEdited, MetronomePreferencesSnapshot.decode(handEdited.encode()))
    }
}
