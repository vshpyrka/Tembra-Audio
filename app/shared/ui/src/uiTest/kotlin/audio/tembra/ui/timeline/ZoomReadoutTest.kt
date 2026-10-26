package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.ZoomReadout
import audio.tembra.ui.timeline.components.zoomLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** The zoom pill (spec FR-015; T099, US3): "1×", "2.5×", "16×", and its menu's fit, in and out. */
@OptIn(ExperimentalTestApi::class)
class ZoomReadoutTest {

    @Test
    fun theLabelRoundsToATenthUnderTenAndWholeAbove() {
        assertEquals("1×", zoomLabel(1.0))
        assertEquals("2.5×", zoomLabel(2.49))
        assertEquals("16×", zoomLabel(16.4))
    }

    @Test
    fun theMenuOffersFitInAndOut() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { TembraTheme { ZoomReadout(zoomFactor = 2.5, onFitProject = { calls += "fit" }, onZoomStep = { calls += "step($it)" }) } }
        val pill = onNodeWithTag(TimelineTestTags.ZOOM).assertContentDescriptionEquals("Zoom 2.5×")
        pill.performClick()
        onNodeWithTag("${TimelineTestTags.ZOOM}.fit").performClick()
        pill.performClick()
        onNodeWithTag("${TimelineTestTags.ZOOM}.in").performClick()
        pill.performClick()
        onNodeWithTag("${TimelineTestTags.ZOOM}.out").performClick()
        assertEquals(listOf("fit", "step(1)", "step(-1)"), calls)
    }
}
