package audio.tembra.ui.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The anchor list is complete, unique and made of stable dotted identifiers. */
class PerfAnchorsCatalogueTest {

    @Test
    fun anchorsAreUniqueAndWellFormed() {
        assertEquals(PerfAnchors.all.size, PerfAnchors.all.toSet().size)
        for (anchor in PerfAnchors.all) {
            assertTrue(Regex("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+").matches(anchor), anchor)
        }
    }

    @Test
    fun everyJourneyControlHasAnAnchor() {
        assertEquals(33, PerfAnchors.all.size)
    }
}
