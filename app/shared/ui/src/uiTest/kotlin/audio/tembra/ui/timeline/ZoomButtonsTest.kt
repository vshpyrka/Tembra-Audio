package audio.tembra.ui.timeline

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.ZoomButtons
import audio.tembra.ui.timeline.components.zoomFactor
import audio.tembra.ui.timeline.components.zoomLevelLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ruler's zoom buttons (spec FR-015, D1; FR-056; T140): "−", the level that fits the project, "+". */
@OptIn(ExperimentalTestApi::class)
class ZoomButtonsTest {

    private val calls = mutableListOf<String>()

    private fun ComposeUiTest.setButtons(label: String = "2.5×") {
        setContent {
            TembraTheme {
                ZoomButtons(
                    levelLabel = label,
                    onZoomOut = { calls += "out" },
                    onZoomIn = { calls += "in" },
                    onFit = { calls += "fit" },
                    modifier = Modifier.testTag(GROUP),
                )
            }
        }
    }

    @Test
    fun theThreeButtonsAreNamedAndTagged() = runComposeUiTest {
        setButtons()
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        onNodeWithTag(TimelineTestTags.ZOOM_OUT).assertContentDescriptionEquals("Zoom out").assertHasClickAction().assert(button)
        onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).assertContentDescriptionEquals("Zoom 2.5×, fit the project").assertHasClickAction().assert(button)
        onNodeWithTag(TimelineTestTags.ZOOM_IN).assertContentDescriptionEquals("Zoom in").assertHasClickAction().assert(button)
    }

    @Test
    fun eachButtonCallsItsOwnCallback() = runComposeUiTest {
        setButtons()
        onNodeWithTag(TimelineTestTags.ZOOM_OUT).performClick()
        onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).performClick()
        onNodeWithTag(TimelineTestTags.ZOOM_IN).performClick()
        assertEquals(listOf("out", "fit", "in"), calls)
    }

    @Test
    fun theTargetsMeasure44dpAndTheGroupFitsIn132dp() = runComposeUiTest {
        // The widest level a long project reaches still fits its segment.
        setButtons(label = "1200×")
        for (tag in listOf(TimelineTestTags.ZOOM_OUT, TimelineTestTags.ZOOM_LEVEL, TimelineTestTags.ZOOM_IN)) {
            onNodeWithTag(tag).assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        }
        val group = onNodeWithTag(GROUP).getUnclippedBoundsInRoot()
        // One pixel of rounding room: the fold emulator's 2.44 density lays 132 dp out as 132.1 dp.
        assertTrue(group.right - group.left <= 132.dp + 1.dp, "the group is ${group.right - group.left} wide")
        val out = onNodeWithTag(TimelineTestTags.ZOOM_OUT).getUnclippedBoundsInRoot()
        val level = onNodeWithTag(TimelineTestTags.ZOOM_LEVEL).getUnclippedBoundsInRoot()
        val zoomIn = onNodeWithTag(TimelineTestTags.ZOOM_IN).getUnclippedBoundsInRoot()
        assertTrue(out.right <= level.left && level.right <= zoomIn.left, "the order is −, level, +: $out $level $zoomIn")
    }

    @Test
    fun theLabelIsTheZoomOverTheWidestLevel() {
        assertEquals("1×", zoomLevelLabel(1.0))
        assertEquals("2.5×", zoomLevelLabel(2.49))
        assertEquals("16×", zoomLevelLabel(16.4))
        // 32 bars of 4/4 across 1000 px at density 2: fit is 1000 / (128 × 2) px per beat.
        val fitted = TimelineViewport.fitted(widthPx = 1000.0, densityScale = 2.0, projectLengthBeats = Beats(64.0), beatsPerBar = 4)
        assertEquals(1.0, fitted.zoomFactor, 1e-9)
        assertEquals("4×", zoomLevelLabel(fitted.copy(pixelsPerBeat = fitted.pixelsPerBeat * 4)))
        assertEquals(1.0, fitted.copy(widthPx = 0.0).zoomFactor, "unmeasured lanes read 1×")
    }

    private companion object {
        const val GROUP = "test.zoomButtons"
    }
}
