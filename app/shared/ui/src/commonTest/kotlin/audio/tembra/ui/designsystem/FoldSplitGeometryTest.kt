package audio.tembra.ui.designsystem

import audio.tembra.ui.posture.TabletopFold
import kotlin.test.Test
import kotlin.test.assertEquals

/** The regions around the fold (data-model.md §3, research R2, R3). */
class FoldSplitGeometryTest {

    @Test
    fun noFoldSplitsInHalfAroundTheGutters() {
        val regions = FoldSplitGeometry.regions(height = 1000, originY = 300f, fold = null, gutter = 20)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 480, lowerTop = 520), regions)
        assertEquals(480, regions.lowerHeight(1000))
    }

    @Test
    fun anUnplacedLayoutSplitsInHalf() {
        val regions = FoldSplitGeometry.regions(height = 1000, originY = Float.NaN, fold = TabletopFold(700f, 700f), gutter = 20)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 480, lowerTop = 520), regions)
    }

    @Test
    fun theFoldIsMeasuredFromTheLayoutsTopInTheWindow() {
        // Content starts 250 px down the window; the fold is at 1038 px: 788 px into the layout.
        val regions = FoldSplitGeometry.regions(height = 1700, originY = 250f, fold = TabletopFold(1038f, 1038f), gutter = 31)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 757, lowerTop = 819), regions)
        assertEquals(881, regions.lowerHeight(1700))
    }

    @Test
    fun aHingeWithHeightIsKeptClearOnBothSides() {
        val regions = FoldSplitGeometry.regions(height = 1000, originY = 0f, fold = TabletopFold(400f, 440f), gutter = 10)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 390, lowerTop = 450), regions)
    }

    @Test
    fun aFoldAboveTheLayoutGivesTheLowerRegionEverything() {
        val regions = FoldSplitGeometry.regions(height = 1000, originY = 500f, fold = TabletopFold(100f, 100f), gutter = 10)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 0, lowerTop = 0), regions)
    }

    @Test
    fun aFoldBelowTheLayoutGivesTheUpperRegionEverything() {
        val regions = FoldSplitGeometry.regions(height = 1000, originY = 0f, fold = TabletopFold(2000f, 2000f), gutter = 10)
        assertEquals(FoldSplitGeometry.Regions(upperHeight = 1000, lowerTop = 1000), regions)
        assertEquals(0, regions.lowerHeight(1000))
    }
}
