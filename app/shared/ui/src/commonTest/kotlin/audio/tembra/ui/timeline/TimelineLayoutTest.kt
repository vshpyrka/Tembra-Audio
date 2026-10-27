package audio.tembra.ui.timeline

import androidx.compose.ui.unit.dp
import audio.tembra.ui.navigation.LayoutMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The four layouts of contracts/timeline-screen.md from the window's size and the shell's layout mode. */
class TimelineLayoutTest {

    @Test
    fun compactUnder600dp() {
        assertEquals(TimelineLayout.Compact, TimelineLayout.from(360.dp, 780.dp, LayoutMode.BottomBar))
        assertEquals(TimelineLayout.Compact, TimelineLayout.from(599.dp, 400.dp, LayoutMode.BottomBar))
    }

    @Test
    fun mediumFrom600To839dpWhenTallEnough() {
        assertEquals(TimelineLayout.Medium, TimelineLayout.from(600.dp, 480.dp, LayoutMode.Rail))
        assertEquals(TimelineLayout.Medium, TimelineLayout.from(839.dp, 900.dp, LayoutMode.Rail))
    }

    @Test
    fun landscapeFrom600dpWideUnder480dpTall() {
        assertEquals(TimelineLayout.Landscape, TimelineLayout.from(780.dp, 360.dp, LayoutMode.Rail))
        assertEquals(TimelineLayout.Landscape, TimelineLayout.from(892.dp, 330.dp, LayoutMode.Rail))
        assertEquals(TimelineLayout.Landscape, TimelineLayout.from(1200.dp, 479.dp, LayoutMode.Rail))
    }

    @Test
    fun expandedFrom840dpWideAnd480dpTall() {
        assertEquals(TimelineLayout.Expanded, TimelineLayout.from(840.dp, 480.dp, LayoutMode.RailWithPanel))
        assertEquals(TimelineLayout.Expanded, TimelineLayout.from(1200.dp, 800.dp, LayoutMode.RailWithPanel))
    }

    @Test
    fun theIphoneDuoEndRailIsCompactWhateverTheSize() {
        assertEquals(TimelineLayout.Compact, TimelineLayout.from(700.dp, 700.dp, LayoutMode.EndRail))
    }

    @Test
    fun onlyExpandedPinsHeadersAndOpensSettingsInline() {
        assertTrue(TimelineLayout.Expanded.pinnedHeaders)
        assertTrue(TimelineLayout.Expanded.inlineSettings)
        for (layout in listOf(TimelineLayout.Compact, TimelineLayout.Medium, TimelineLayout.Landscape)) {
            assertFalse(layout.pinnedHeaders, "$layout pins headers")
            assertFalse(layout.inlineSettings, "$layout opens settings inline")
        }
    }
}
