package audio.tembra.ui.projects

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.projects.components.ProjectOverviewChart
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The card's overview (spec FR-054; T118): bars and the line in the spans' tints, nothing for an empty project. */
@OptIn(ExperimentalTestApi::class)
class ProjectOverviewChartTest {

    private val tints = DawColors.Default.trackTints
    private val overview = CardOverview(48, ByteArray(48) { -1 }, ByteArray(48) { (it % 2).toByte() })

    private fun ComposeUiTest.setChart(overview: CardOverview, asLine: Boolean) {
        setContent { TembraTheme { ProjectOverviewChart(overview = overview, asLine = asLine, modifier = Modifier.width(320.dp), testTag = TAG) } }
    }

    private fun ComposeUiTest.hasPixelNear(color: Color): Boolean {
        val pixels = onNodeWithTag(TAG).captureToImage().toPixelMap()
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val p = pixels[x, y]
            if (abs(p.red - color.red) < 0.05f && abs(p.green - color.green) < 0.05f && abs(p.blue - color.blue) < 0.05f) return true
        }
        return false
    }

    @Test
    fun barsCarryTheSpansTints() = runComposeUiTest {
        setChart(overview, asLine = false)
        assertTrue(hasPixelNear(tints[0]), "no bar in the first tint")
        assertTrue(hasPixelNear(tints[1]), "no bar in the second tint")
    }

    @Test
    fun theLineFormDrawsToo() = runComposeUiTest {
        setChart(overview, asLine = true)
        assertTrue(hasPixelNear(tints[0]) || hasPixelNear(tints[1]), "no line segment in a span tint")
    }

    @Test
    fun anEmptyProjectDrawsNothing() = runComposeUiTest {
        setChart(CardOverview.Empty, asLine = false)
        assertFalse(hasPixelNear(tints[0]))
        assertFalse(hasPixelNear(tints[1]))
    }

    private companion object {
        const val TAG = "chart"
    }
}
