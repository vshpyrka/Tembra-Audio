package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The worked examples of spec "How the rhythm controls fit together" and the debug readings (FR-037). */
class TickGridTest {

    private fun grid(
        signature: TimeSignature,
        subdivision: Subdivision,
        style: MetronomeStyle,
        reading: SyncopationReading = SyncopationReading.EvenBeats,
    ): TickGrid = TickGrid.from(signature, subdivision, style.pattern(signature), style, reading)

    private fun TickGrid.text(): String = levels.joinToString("") {
        when (it) {
            BeatLevel.Accent -> "A"
            BeatLevel.Click -> "C"
            BeatLevel.Sub -> "s"
        }
    }

    @Test
    fun workedExamples() {
        assertEquals("ACCC", grid(TimeSignature.FourFour, Subdivision.One, MetronomeStyle.Standard).text())
        assertEquals("AsCsCsCs", grid(TimeSignature.FourFour, Subdivision.Two, MetronomeStyle.Standard).text())
        assertEquals("Asss", grid(TimeSignature.FourFour, Subdivision.One, MetronomeStyle.Downbeat).text())
        assertEquals("CACA", grid(TimeSignature.FourFour, Subdivision.One, MetronomeStyle.Syncopated).text())
        assertEquals("ACCACC", grid(TimeSignature.SixEight, Subdivision.One, MetronomeStyle.Standard).text())
        assertEquals("CACACAC", grid(TimeSignature.SevenEight, Subdivision.One, MetronomeStyle.Syncopated).text())
        assertEquals("AssCssCss", grid(TimeSignature.ThreeFour, Subdivision.Triplet, MetronomeStyle.Standard).text())
    }

    @Test
    fun syncopatedReadings() {
        assertEquals(
            "sAsAsAsA",
            grid(TimeSignature.FourFour, Subdivision.Two, MetronomeStyle.Syncopated, SyncopationReading.OffBeatTicks).text(),
        )
        assertEquals(
            "sCACACAC",
            grid(TimeSignature.FourFour, Subdivision.Two, MetronomeStyle.Syncopated, SyncopationReading.AllButOne).text(),
        )
    }

    @Test
    fun offBeatTicksWithOneTickPerBeatEqualsEvenBeats() {
        assertEquals(
            grid(TimeSignature.FourFour, Subdivision.One, MetronomeStyle.Syncopated, SyncopationReading.EvenBeats),
            grid(TimeSignature.FourFour, Subdivision.One, MetronomeStyle.Syncopated, SyncopationReading.OffBeatTicks),
        )
    }

    @Test
    fun otherReadingsAreIgnoredOutsideSyncopated() {
        for (style in listOf(MetronomeStyle.Standard, MetronomeStyle.Downbeat)) {
            val baseline = grid(TimeSignature.FourFour, Subdivision.Two, style)
            for (reading in SyncopationReading.entries) {
                assertEquals(baseline, grid(TimeSignature.FourFour, Subdivision.Two, style, reading), "${style.id} ${reading.id}")
            }
        }
        val edited = AccentPattern("ACSC".map { BeatLevel.byId(it)!! })
        val noStyle = TickGrid.from(TimeSignature.FourFour, Subdivision.Two, edited, null)
        for (reading in SyncopationReading.entries) {
            assertEquals(noStyle, TickGrid.from(TimeSignature.FourFour, Subdivision.Two, edited, null, reading))
        }
    }

    @Test
    fun reportsTicksPerBeatAndBeats() {
        val triplets = grid(TimeSignature.ThreeFour, Subdivision.Triplet, MetronomeStyle.Standard)
        assertEquals(3, triplets.ticksPerBeat)
        assertEquals(3, triplets.beats)
        assertEquals(9, triplets.levels.size)
    }

    @Test
    fun theLargestGridFitsTheEngineLimit() {
        val largest = grid(TimeSignature.SevenEight, Subdivision.Four, MetronomeStyle.Standard)
        assertEquals(28, largest.levels.size)
        assertTrue(largest.levels.size <= 32)
    }
}
