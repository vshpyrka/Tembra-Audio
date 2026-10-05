package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The noise gate slider on its own (feature 006, contracts/tuner-screen.md §2; spec FR-001, FR-002). */
@OptIn(ExperimentalTestApi::class)
class NoiseGateSliderTest {

    private val reported = mutableListOf<NoiseGate>()
    private var settled = 0

    /** Renders the slider 320 dp wide over an opaque surface and returns a reader of the hoisted value. */
    private fun ComposeUiTest.setSlider(initial: NoiseGate = NoiseGate.Off, width: Dp = 320.dp): () -> NoiseGate {
        var gate by mutableStateOf(initial)
        setContent {
            TembraTheme {
                Box(Modifier.width(width).background(TembraTheme.colors.surface)) {
                    NoiseGateSlider(
                        gate = gate,
                        onGate = {
                            reported += it
                            gate = it
                        },
                        onGateSettled = { settled++ },
                    )
                }
            }
        }
        waitForIdle()
        return { gate }
    }

    /**
     * The x of [step] on the slider node, in pixels: Material lays the track out between the first and the last
     * position of the handle's centre, and the handle's box is a 44 dp touch target (spec FR-018).
     */
    private fun stepX(step: Int, nodeWidth: Float, density: Float): Float {
        val inset = 22f * density
        return inset + (nodeWidth - 2 * inset) * step / NoiseGate.STEPS
    }

    @Test
    fun showsTheLabelTheTwoEndLabelsAndNoDecibelFigure() = runComposeUiTest {
        setSlider()
        onNodeWithTag("tuner.noiseGate").assertExists()
        onNodeWithTag("tuner.noiseGate.slider").assertExists()
        onNodeWithTag("tuner.noiseGate.handle", useUnmergedTree = true).assertExists()
        val inside = hasAnyAncestor(hasTestTag("tuner.noiseGate"))
        onNode(hasText("NOISE GATE") and inside, useUnmergedTree = true).assertExists()
        onNode(hasText("Off") and inside, useUnmergedTree = true).assertExists()
        onNode(hasText("High") and inside, useUnmergedTree = true).assertExists()
        onAllNodes(hasText("dB", substring = true, ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aClickOnTheTrackMovesTheHandleThere() = runComposeUiTest {
        val gate = setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(10, width.toFloat(), density), centerY)) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(10), gate())
        assertEquals(-60, gate().levelDb)
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(20, width.toFloat(), density), centerY)) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(20), gate())
        assertEquals(listOf(NoiseGate.ofStep(10), NoiseGate.ofStep(20)), reported)
    }

    @Test
    fun aSidewaysDragReportsRisingSteps() = runComposeUiTest {
        val gate = setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(Offset(stepX(0, width.toFloat(), density), centerY))
            for (step in 1..12) moveTo(Offset(stepX(step, width.toFloat(), density), centerY))
            up()
        }
        waitForIdle()
        // Material starts a drag once the touch slop is passed, so the handle trails the finger by the slop: it ends a
        // few steps short of the twelve the finger travelled, as the tempo slider's does.
        assertTrue(gate().step in 8..12, "the handle ended at step ${gate().step}")
        assertEquals(reported.last(), gate())
        assertTrue(reported.size >= 3, "only ${reported.size} steps were reported")
        assertEquals(reported.sortedBy { it.step }, reported, "the steps did not rise: ${reported.map { it.step }}")
        assertEquals(reported.distinct(), reported, "a step was reported twice: ${reported.map { it.step }}")
    }

    @Test
    fun theHandleStopsAtBothEnds() = runComposeUiTest {
        val gate = setSlider(NoiseGate.ofStep(15))
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(center)
            moveTo(Offset(-200f, centerY))
            up()
        }
        waitForIdle()
        assertEquals(NoiseGate.Off, gate())
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(center)
            moveTo(Offset(width + 200f, centerY))
            up()
        }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(NoiseGate.STEPS), gate())
        assertEquals(-20, gate().levelDb)
    }

    @Test
    fun anUnchangedStepIsNotReported() = runComposeUiTest {
        setSlider(NoiseGate.ofStep(15))
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(center) }
        waitForIdle()
        assertEquals(emptyList(), reported)
    }
}
