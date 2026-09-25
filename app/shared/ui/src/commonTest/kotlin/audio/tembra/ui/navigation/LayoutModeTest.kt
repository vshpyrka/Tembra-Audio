package audio.tembra.ui.navigation

import androidx.window.core.layout.WindowSizeClass
import audio.tembra.ui.theme.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every row of data-model.md §3, on the JVM and the iOS simulator (spec FR-004 to FR-006, SC-004). */
class LayoutModeTest {

    @Test
    fun compactWidthAlwaysUsesTheBottomBar() {
        assertEquals(LayoutMode.BottomBar, LayoutMode.from(WindowWidth.Compact, isShort = false))
        assertEquals(LayoutMode.BottomBar, LayoutMode.from(WindowWidth.Compact, isShort = true))
    }

    @Test
    fun mediumWidthAndShortExpandedWindowsUseTheRail() {
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowWidth.Medium, isShort = false))
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowWidth.Medium, isShort = true))
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowWidth.Expanded, isShort = true))
    }

    @Test
    fun tallExpandedWindowsUseTheRailWithThePanel() {
        assertEquals(LayoutMode.RailWithPanel, LayoutMode.from(WindowWidth.Expanded, isShort = false))
    }

    @Test
    fun windowSizeClassesMapToTheLayoutMode() {
        assertEquals(LayoutMode.BottomBar, LayoutMode.from(WindowSizeClass(599, 800)))
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowSizeClass(600, 800)))
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowSizeClass(839, 800)))
        assertEquals(LayoutMode.Rail, LayoutMode.from(WindowSizeClass(1024, 479)))
        assertEquals(LayoutMode.RailWithPanel, LayoutMode.from(WindowSizeClass(840, 480)))
        assertEquals(LayoutMode.RailWithPanel, LayoutMode.from(WindowSizeClass(1024, 800)))
    }

    /** SC-004: crossing 600 dp, 840 dp and 480 dp in both directions switches the mode at every step. */
    @Test
    fun crossingEveryThresholdInBothDirectionsSwitchesTheMode() {
        val resizes = listOf(
            599 to 800, 600 to 800, 599 to 800,
            839 to 800, 840 to 800, 839 to 800,
            1024 to 479, 1024 to 480, 1024 to 479,
        )
        val expected = listOf(
            LayoutMode.BottomBar, LayoutMode.Rail, LayoutMode.BottomBar,
            LayoutMode.Rail, LayoutMode.RailWithPanel, LayoutMode.Rail,
            LayoutMode.Rail, LayoutMode.RailWithPanel, LayoutMode.Rail,
        )
        assertEquals(expected, resizes.map { (width, height) -> LayoutMode.from(WindowSizeClass(width, height)) })
    }

    @Test
    fun windowSizeClassesMapToTheWidthBucket() {
        assertEquals(WindowWidth.Compact, WindowWidth.from(WindowSizeClass(599, 800)))
        assertEquals(WindowWidth.Medium, WindowWidth.from(WindowSizeClass(600, 800)))
        assertEquals(WindowWidth.Medium, WindowWidth.from(WindowSizeClass(839, 800)))
        assertEquals(WindowWidth.Expanded, WindowWidth.from(WindowSizeClass(840, 800)))
    }
}
