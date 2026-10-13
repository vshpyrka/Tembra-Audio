package audio.tembra.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the timeline arranger's tokens to contracts/design-system-additions.md of feature 009. */
class TimelineTokensTest {

    @Test
    fun sixTrackTintsResolveAndStartWithTheExistingClipTints() {
        val daw = DawColors.Default
        assertEquals(6, daw.trackTints.size)
        assertEquals(daw.lavenderLight, daw.trackTints[0])
        assertEquals(daw.roseLight, daw.trackTints[1])
        assertEquals(6, daw.trackTints.toSet().size, "every tint is distinct")
        for (tint in daw.trackTints) assertEquals(1f, tint.alpha, "track tints are opaque")
    }

    @Test
    fun clipTokensResolveToTheirSources() {
        val daw = DawColors.Default
        assertEquals(daw.surfaceLevel2.copy(alpha = 0.7f).toArgbEquivalent(), daw.clipCaptionBand.toArgbEquivalent())
        assertEquals(daw.lavenderLight, daw.playheadChip)
        assertEquals(TembraColorScheme.dark.primary, daw.selectionOutline)
        assertEquals(TembraColorScheme.dark.outlineVariant, daw.unavailableClip)
    }

    @Test
    fun spacingCarriesTheTimelineMetrics() {
        val spacing = TembraSpacing()
        assertEquals(88.dp, spacing.laneHeightCompact)
        assertEquals(96.dp, spacing.laneHeightExpanded)
        assertEquals(44.dp, spacing.rulerHeight)
        assertEquals(48.dp, spacing.toolbarHeight)
        assertEquals(64.dp, spacing.transportHeightCompact)
        assertEquals(72.dp, spacing.transportHeightExpanded)
        assertEquals(200.dp, spacing.headerColumnWidth)
        assertEquals(56.dp, spacing.cardOverviewHeight)
        // Three lanes, the ruler, the toolbar and the transport fit a 640 dp tall phone above its navigation bar.
        assertTrue(3 * spacing.laneHeightCompact + spacing.rulerHeight + spacing.toolbarHeight + spacing.transportHeightCompact <= 640.dp)
    }

    @Test
    fun clipShapeIsTheMediumRadius() {
        assertEquals(TembraShapes.medium, TembraShapes.clip)
        assertEquals(12.dp, TembraShapes.clip)
    }

    @Test
    fun iconRegistryCarriesTheTimelineGlyphs() {
        val names = TembraIcon.entries.map { it.name }
        for (expected in listOf(
            "ContentCut", "Undo", "Redo", "SkipPrevious", "SkipNext", "FastRewind", "FastForward", "GridOn", "ZoomIn", "ZoomOut",
            "Search", "Sort", "ContentCopy", "LibraryMusic", "ExpandMore", "ExpandLess", "MoreVert", "AudioFile", "PlaylistAdd", "Mic", "Piano",
        )) {
            assertTrue(expected in names, "TembraIcon.$expected is missing")
        }
    }

    /** Colour equality to 8 bits per channel (the alpha of the caption band is a rounded 0.7). */
    private fun Color.toArgbEquivalent(): List<Int> = listOf(alpha, red, green, blue).map { (it * 255f + 0.5f).toInt() }
}
