package audio.tembra.ui.tuner

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
