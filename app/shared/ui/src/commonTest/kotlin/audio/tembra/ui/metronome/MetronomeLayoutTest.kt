package audio.tembra.ui.metronome

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
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
}
