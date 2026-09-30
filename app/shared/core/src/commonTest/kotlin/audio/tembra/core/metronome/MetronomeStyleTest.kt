package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The three styles as presets for the accent cells (spec FR-010). */
class MetronomeStyleTest {

    private fun expected(signature: TimeSignature, accent: (Int) -> Boolean, other: BeatLevel): List<BeatLevel> =
        List(signature.beats) { index -> if (accent(index + 1)) BeatLevel.Accent else other }

    @Test
    fun standardAccentsBeatOneAndSixEightAlsoBeatFour() {
        for (signature in TimeSignature.entries) {
            val accentBeats: (Int) -> Boolean =
                if (signature == TimeSignature.SixEight) { beat -> beat == 1 || beat == 4 } else { beat -> beat == 1 }
            assertEquals(
                expected(signature, accentBeats, BeatLevel.Click),
                MetronomeStyle.Standard.pattern(signature).levels,
                signature.id,
            )
        }
    }

    @Test
    fun downbeatAccentsBeatOneAndSoftensTheRest() {
        for (signature in TimeSignature.entries) {
            assertEquals(
                expected(signature, { it == 1 }, BeatLevel.Sub),
                MetronomeStyle.Downbeat.pattern(signature).levels,
                signature.id,
            )
        }
    }

    @Test
    fun syncopatedAccentsTheEvenBeats() {
        for (signature in TimeSignature.entries) {
            assertEquals(
                expected(signature, { it % 2 == 0 }, BeatLevel.Click),
                MetronomeStyle.Syncopated.pattern(signature).levels,
                signature.id,
            )
        }
    }

    @Test
    fun matchingRoundTripsEveryStyleAndSignature() {
        for (style in MetronomeStyle.entries) {
            for (signature in TimeSignature.entries) {
                assertEquals(style, MetronomeStyle.matching(style.pattern(signature), signature), "${style.id} ${signature.id}")
            }
        }
    }

    @Test
    fun aHandEditedPatternMatchesNoStyle() {
        val edited = AccentPattern("ACSC".map { BeatLevel.byId(it)!! })
        assertNull(MetronomeStyle.matching(edited, TimeSignature.FourFour))
    }

    @Test
    fun idsLabelsAndLookup() {
        assertEquals(listOf("standard", "downbeat", "syncopated"), MetronomeStyle.entries.map { it.id })
        assertEquals(listOf("Standard", "Downbeat", "Syncopated"), MetronomeStyle.entries.map { it.label })
        assertEquals(MetronomeStyle.Downbeat, MetronomeStyle.byId("downbeat"))
        assertNull(MetronomeStyle.byId("nope"))
    }

    @Test
    fun readingsHaveTheirIds() {
        assertEquals(
            listOf("even-beats", "off-beat-ticks", "all-but-one"),
            SyncopationReading.entries.map { it.id },
        )
    }
}
