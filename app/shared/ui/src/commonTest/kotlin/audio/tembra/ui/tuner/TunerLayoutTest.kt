package audio.tembra.ui.tuner

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
}
