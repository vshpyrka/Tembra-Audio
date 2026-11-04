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
            assertTrue(Regex("[a-z][A-Za-z0-9]*(\\.[a-z0-9][A-Za-z0-9]*)+").matches(anchor), anchor)
        }
    }

    @Test
    fun everyJourneyControlHasAnAnchor() {
        assertEquals(38, PerfAnchors.all.size)
    }

    @Test
    fun theWindowingJourneysControlsAreAnchors() {
        // Feature 011 (contracts/perf-instrumentation.md): the right-clicked clip's menu and the shortcut sheet.
        assertEquals("timeline.clip.first.menu", PerfAnchors.TIMELINE_CLIP_MENU_FIRST)
        assertEquals("shortcuts.sheet", PerfAnchors.SHORTCUT_SHEET)
        assertTrue(PerfAnchors.TIMELINE_CLIP_MENU_FIRST in PerfAnchors.all && PerfAnchors.SHORTCUT_SHEET in PerfAnchors.all)
    }
}
