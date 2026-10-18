package audio.tembra.ui.tuner

import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.theme.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tuner's layout from the window's width bucket and height (spec FR-023, data-model.md §8). */
class TunerLayoutTest {

    @Test
    fun compactWindowsAreAlwaysStacked() {
        assertEquals(TunerLayout.Stacked, TunerLayout.from(WindowWidth.Compact, isShort = false))
        assertEquals(TunerLayout.Stacked, TunerLayout.from(WindowWidth.Compact, isShort = true))
    }

    @Test
    fun tallWindowsFollowTheWidth() {
        assertEquals(TunerLayout.Stacked, TunerLayout.from(WindowWidth.Medium, isShort = false))
        assertEquals(TunerLayout.Wide, TunerLayout.from(WindowWidth.Expanded, isShort = false))
    }

    @Test
    fun shortWideWindowsAreLandscape() {
        assertEquals(TunerLayout.Landscape, TunerLayout.from(WindowWidth.Medium, isShort = true))
        assertEquals(TunerLayout.Landscape, TunerLayout.from(WindowWidth.Expanded, isShort = true))
    }

    /**
     * The tablet survey (2026-10-16): desktop windows of 772 × 511, 560 × 480 and 420 × 380 dp leave the screen 463, 368
     * and 268 dp of content now that the shell has no top bar (407, 312 and 212 dp under it, where the stacked meter was
     * never seen whole); the line moved with the bar's 56 dp, so the same windows take the Pocket form as before.
     */
    @Test
    fun aStackedScreenWithUnder506DpOfContentTakesThePocketForm() {
        for (height in listOf(463, 368, 268, 505)) {
            assertEquals(TunerLayout.Pocket, TunerLayout.Stacked.forContentHeight(height.dp), "$height dp")
        }
        for (height in listOf(506, 584, 660, 800)) {
            assertEquals(TunerLayout.Stacked, TunerLayout.Stacked.forContentHeight(height.dp), "$height dp")
        }
    }

    @Test
    fun onlyTheStackedFormGivesWayToAShortScreen() {
        for (layout in listOf(TunerLayout.Wide, TunerLayout.Landscape, TunerLayout.Tabletop, TunerLayout.Pocket)) {
            assertEquals(layout, layout.forContentHeight(200.dp))
        }
    }

    @Test
    fun aTabletopFoldWinsOverEveryBand() {
        val fold = TabletopFold(top = 1038f, bottom = 1038f)
        for ((width, height) in listOf(883 to 852, 360 to 800, 900 to 400, 1280 to 800)) {
            val sizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(width, height)
            assertEquals(TunerLayout.Tabletop, TunerLayout.from(sizeClass, fold), "$width x $height")
            assertEquals(TunerLayout.from(sizeClass), TunerLayout.from(sizeClass, fold = null), "$width x $height without a fold")
        }
    }
}
