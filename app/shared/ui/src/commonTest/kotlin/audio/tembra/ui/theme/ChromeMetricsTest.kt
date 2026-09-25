package audio.tembra.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the chrome metrics of data-model.md §6 so an accidental edit fails a test (spec FR-019). */
class ChromeMetricsTest {

    private val spacing = TembraSpacing()

    @Test
    fun frameHeightsAndWidthsMatchTheDesign() {
        assertEquals(56.dp, spacing.topBarHeight)
        assertEquals(64.dp, spacing.bottomBarHeight)
        assertEquals(64.dp, spacing.railWidth)
        assertEquals(320.dp, spacing.supportingPaneWidth)
    }

    @Test
    fun navigationItemMetricsMatchTheDesign() {
        assertEquals(56.dp, spacing.navItemMinWidth)
        assertEquals(48.dp, spacing.railItemWidth)
        assertEquals(20.dp, spacing.navBarIconSize)
        assertEquals(22.dp, spacing.navRailIconSize)
        assertEquals(4.dp, spacing.navItemGap)
        assertEquals(2.dp, spacing.focusRingWidth)
    }
}
