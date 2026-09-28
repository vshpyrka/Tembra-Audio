package audio.tembra.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The design's 32 × 16 dp switch (contracts/design-system-additions.md, spec FR-024, FR-027). */
@OptIn(ExperimentalTestApi::class)
class TembraToggleTest {

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private var secondary = Color.Unspecified
    private var highest = Color.Unspecified

    private fun ComposeUiTest.setToggle(checked: Boolean, onChange: (Boolean) -> Unit = {}) {
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                highest = TembraTheme.colors.surfaceContainerHighest
                TembraToggle(checked = checked, onCheckedChange = onChange, testTag = "tg")
            }
        }
    }

    /** The track's centre column away from the knob: the left third when on (knob right), the right third when off. */
    private fun ComposeUiTest.trackColor(checked: Boolean): Color {
        val image = onNodeWithTag("tg.track", useUnmergedTree = true).captureToImage()
        val x = if (checked) image.width / 5 else image.width * 4 / 5
        return image.averageColorAt(x, image.height / 2)
    }

    @Test
    fun switchSemanticsAndCallback() = runComposeUiTest {
        var changed: Boolean? = null
        setToggle(checked = true, onChange = { changed = it })
        onNodeWithTag("tg").assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        onNodeWithTag("tg").performClick()
        assertEquals(false, changed)
    }

    @Test
    fun drawnTrackIs32By16InsideA44dpTarget() = runComposeUiTest {
        setToggle(checked = false)
        onNodeWithTag("tg.track", useUnmergedTree = true).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(16.dp)
        onNodeWithTag("tg").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        onNodeWithTag("tg").assertIsOff()
    }

    @Test
    fun trackColourFollowsTheState() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                highest = TembraTheme.colors.surfaceContainerHighest
                TembraToggle(checked = checked, onCheckedChange = { checked = it }, testTag = "tg")
            }
        }
        assertTrue(trackColor(false).approximately(highest, 0.05f), "off track ${trackColor(false)} is not surfaceContainerHighest")
        checked = true
        waitForIdle()
        assertTrue(trackColor(true).approximately(secondary, 0.05f), "on track ${trackColor(true)} is not secondary")
    }

    @Test
    fun theKnobMovesWithinAFrameUnderReduceMotion() = runComposeUiTest(effectContext = zeroScale) {
        var checked by mutableStateOf(false)
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                TembraToggle(checked = checked, onCheckedChange = { checked = it }, testTag = "tg")
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("tg").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("tg").assertIsOn()
        val image = onNodeWithTag("tg.track", useUnmergedTree = true).captureToImage()
        val knobSide = image.averageColorAt(image.width * 4 / 5, image.height / 2)
        assertTrue(!knobSide.approximately(secondary, 0.05f), "knob did not reach the right side within a frame: $knobSide")
    }
}
