package audio.tembra.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the chrome metrics of data-model.md §6 so an accidental edit fails a test (spec FR-019). The bar and rail take
 * their sizes from Material 3 Expressive since 2026-10-17, so only the top bar component, the side panel and the focus
 * ring keep tokens here.
 */
class ChromeMetricsTest {

    private val spacing = TembraSpacing()

    @Test
    fun frameHeightsAndWidthsMatchTheDesign() {
        assertEquals(56.dp, spacing.topBarHeight)
        assertEquals(320.dp, spacing.supportingPaneWidth)
    }

    @Test
    fun focusRingMatchesTheDesign() {
        assertEquals(2.dp, spacing.focusRingWidth)
    }
}
