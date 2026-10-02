package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals

/** Italian tempo names: the boundary belongs to the slower name (spec FR-005). */
class TempoMarkingTest {

    @Test
    fun everyTempoMapsToTheFirstEntryWhoseHighIsNotBelowIt() {
        for (bpm in Tempo.MIN..Tempo.MAX) {
            val expected = TempoMarking.entries.first { it.high >= bpm }
            assertEquals(expected, TempoMarking.of(Tempo(bpm)), "bpm $bpm")
        }
    }

    @Test
    fun boundariesBelongToTheSlowerName() {
        assertEquals(TempoMarking.Largo, TempoMarking.of(Tempo(60)))
        assertEquals(TempoMarking.Larghetto, TempoMarking.of(Tempo(61)))
        assertEquals(TempoMarking.Larghetto, TempoMarking.of(Tempo(66)))
        assertEquals(TempoMarking.Adagio, TempoMarking.of(Tempo(76)))
        assertEquals(TempoMarking.Andante, TempoMarking.of(Tempo(108)))
        assertEquals(TempoMarking.Moderato, TempoMarking.of(Tempo(120)))
        assertEquals(TempoMarking.Allegro, TempoMarking.of(Tempo(121)))
        assertEquals(TempoMarking.Allegro, TempoMarking.of(Tempo(156)))
        assertEquals(TempoMarking.Vivace, TempoMarking.of(Tempo(176)))
        assertEquals(TempoMarking.Presto, TempoMarking.of(Tempo(200)))
        assertEquals(TempoMarking.Prestissimo, TempoMarking.of(Tempo(240)))
    }

    @Test
    fun displayShowsTheRange() {
        assertEquals("Moderato (108-120)", TempoMarking.Moderato.display)
    }

    @Test
    fun theNineEntriesAreInSlowToFastOrder() {
        val table = TempoMarking.entries.map { Triple(it.label, it.low, it.high) }
        assertEquals(
            listOf(
                Triple("Largo", 40, 60),
                Triple("Larghetto", 60, 66),
                Triple("Adagio", 66, 76),
                Triple("Andante", 76, 108),
                Triple("Moderato", 108, 120),
                Triple("Allegro", 120, 156),
                Triple("Vivace", 156, 176),
                Triple("Presto", 176, 200),
                Triple("Prestissimo", 200, 240),
            ),
            table,
        )
    }
}
