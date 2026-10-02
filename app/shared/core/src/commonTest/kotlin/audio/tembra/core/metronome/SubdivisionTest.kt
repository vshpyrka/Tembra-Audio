package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ticks per beat and the labels that follow the beat unit (spec FR-007). */
class SubdivisionTest {

    @Test
    fun entriesAreInDisplayOrderWithTheirTickCounts() {
        assertEquals(
            listOf(Subdivision.One, Subdivision.Two, Subdivision.Four, Subdivision.Triplet),
            Subdivision.entries,
        )
        assertEquals(listOf(1, 2, 4, 3), Subdivision.entries.map { it.ticksPerBeat })
    }

    @Test
    fun labelsFollowTheBeatUnit() {
        assertEquals(listOf("1/4", "1/8", "1/16", "3let"), Subdivision.entries.map { it.label(BeatUnit.Quarter) })
        assertEquals(listOf("1/8", "1/16", "1/32", "3let"), Subdivision.entries.map { it.label(BeatUnit.Eighth) })
    }

    @Test
    fun byIdParsesKnownIdsOnly() {
        assertEquals(Subdivision.Triplet, Subdivision.byId("3"))
        assertNull(Subdivision.byId("5"))
        assertEquals(Subdivision.One, Subdivision.Default)
    }
}
