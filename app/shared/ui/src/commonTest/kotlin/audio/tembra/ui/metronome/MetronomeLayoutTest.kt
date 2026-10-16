package audio.tembra.ui.metronome

import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.theme.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals

/** The metronome's layout from the window (data-model.md §9): the tuner's bands. */
class MetronomeLayoutTest {

    private fun layoutOf(widthDp: Int, heightDp: Int): MetronomeLayout =
        MetronomeLayout.from(WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp, heightDp))

    @Test
    fun compactWindowsAreAlwaysStacked() {
        assertEquals(MetronomeLayout.Stacked, MetronomeLayout.from(WindowWidth.Compact, isShort = false))
        assertEquals(MetronomeLayout.Stacked, MetronomeLayout.from(WindowWidth.Compact, isShort = true))
    }

    @Test
    fun tallMediumWindowsAreStackedAndTallExpandedOnesWide() {
        assertEquals(MetronomeLayout.Stacked, MetronomeLayout.from(WindowWidth.Medium, isShort = false))
        assertEquals(MetronomeLayout.Wide, MetronomeLayout.from(WindowWidth.Expanded, isShort = false))
    }

    @Test
    fun shortWindowsFromMediumWidthAreLandscape() {
        assertEquals(MetronomeLayout.Landscape, MetronomeLayout.from(WindowWidth.Medium, isShort = true))
        assertEquals(MetronomeLayout.Landscape, MetronomeLayout.from(WindowWidth.Expanded, isShort = true))
    }

    @Test
    fun measuredWindows() {
        assertEquals(MetronomeLayout.Stacked, layoutOf(360, 800))
        assertEquals(MetronomeLayout.Stacked, layoutOf(600, 900))
        assertEquals(MetronomeLayout.Wide, layoutOf(840, 500))
        assertEquals(MetronomeLayout.Landscape, layoutOf(900, 400))
        assertEquals(MetronomeLayout.Wide, layoutOf(1024, 768))
    }

    /**
     * The tablet survey (2026-10-16): desktop windows of 772 × 511, 560 × 480 and 420 × 380 dp leave the screen 463, 368
     * and 268 dp of content now that the shell has no top bar (407, 312 and 212 dp under it), too little for the stacked
     * card over the dial, which ends under the fold; so does a 360 × 640 dp phone (505 dp). A 360 × 720 dp phone with
     * three-button navigation (584 dp) and the survey's 700 × 708 dp window (660 dp) keep the stacked card.
     */
    @Test
    fun aStackedScreenWithUnder506DpOfContentTakesThePocketForm() {
        for (height in listOf(463, 368, 268, 505)) {
            assertEquals(MetronomeLayout.Pocket, MetronomeLayout.Stacked.forContentHeight(height.dp), "$height dp")
        }
        for (height in listOf(506, 584, 660, 800)) {
            assertEquals(MetronomeLayout.Stacked, MetronomeLayout.Stacked.forContentHeight(height.dp), "$height dp")
        }
    }

    @Test
    fun onlyTheStackedFormGivesWayToAShortScreen() {
        for (layout in listOf(MetronomeLayout.Wide, MetronomeLayout.Landscape, MetronomeLayout.Tabletop, MetronomeLayout.Pocket)) {
            assertEquals(layout, layout.forContentHeight(200.dp))
        }
    }

    @Test
    fun aTabletopFoldWinsOverEveryBand() {
        val fold = TabletopFold(top = 1038f, bottom = 1038f)
        for ((width, height) in listOf(883 to 852, 360 to 800, 900 to 400, 1280 to 800)) {
            val sizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(width, height)
            assertEquals(MetronomeLayout.Tabletop, MetronomeLayout.from(sizeClass, fold), "$width x $height")
            assertEquals(MetronomeLayout.from(sizeClass), MetronomeLayout.from(sizeClass, fold = null), "$width x $height without a fold")
        }
    }
}
