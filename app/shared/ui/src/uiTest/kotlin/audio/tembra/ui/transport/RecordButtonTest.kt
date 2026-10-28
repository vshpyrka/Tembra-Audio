package audio.tembra.ui.transport

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Record control's semantics for each state, its target size and its stillness under reduce motion (spec FR-007, FR-029). */
@OptIn(ExperimentalTestApi::class)
class RecordButtonTest {

    private var taps = 0

    private fun ComposeUiTest.setButton(
        state: RecordButtonState,
        beats: Int = -1,
        enabled: Boolean = true,
        reduceMotion: Boolean = false,
    ) {
        setContent {
            val motion = if (reduceMotion) TembraMotion(reduceMotion = true) else LocalTembraMotion.current
            CompositionLocalProvider(LocalTembraMotion provides motion) {
                TembraTheme {
                    RecordButton(state = state, onRecord = { taps++ }, countInBeatsLeft = beats, enabled = enabled)
                }
            }
        }
        mainClock.autoAdvance = false
    }

    @Test
    fun hiddenComposesNothing() = runComposeUiTest {
        setButton(RecordButtonState.Hidden)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertDoesNotExist()
    }

    @Test
    fun idleIsNamedRecordAndForwardsTheClick() = runComposeUiTest {
        setButton(RecordButtonState.Idle)
        mainClock.autoAdvance = true
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals(TransportCopy.record).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun theCountInNamesTheBeatsLeft() = runComposeUiTest {
        setButton(RecordButtonState.CountIn, beats = 3)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 3 beats left")
    }

    @Test
    fun theLastCountInBeatIsSingular() = runComposeUiTest {
        setButton(RecordButtonState.CountIn, beats = 1)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 1 beat left")
    }

    @Test
    fun recordingIsNamedRecording() = runComposeUiTest {
        setButton(RecordButtonState.Recording)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals(TransportCopy.recording)
    }

    @Test
    fun disabledIsNotEnabled() = runComposeUiTest {
        setButton(RecordButtonState.Idle, enabled = false)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertIsNotEnabled()
    }

    @Test
    fun theTargetIs44dp() = runComposeUiTest {
        setButton(RecordButtonState.Idle)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun underReduceMotionTheCountInStaysStableAcrossBeats() = runComposeUiTest {
        var beats by mutableStateOf(3)
        setContent {
            CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = true)) {
                TembraTheme { RecordButton(state = RecordButtonState.CountIn, onRecord = {}, countInBeatsLeft = beats) }
            }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(500)
        beats = 2
        mainClock.advanceTimeBy(500)
        onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).assertContentDescriptionEquals("Count-in, 2 beats left")
    }

    /**
     * The pulse follows the engine's beats (spec FR-007, FR-015; T062): the ring is drawn after a beat change and gone
     * once the pulse has run out, and the engine's next beat restarts it. Read as pixel differences across time on the
     * button's middle row outside the disc, so no colour blending has to be assumed.
     */
    @Test
    fun theRingPulsesOnEachBeatTheEngineReportsAndRestsBetween() = runComposeUiTest {
        var beats = 4
        val telemetry = ScriptedTransportTelemetry { TransportSnapshot().apply { countInBeatsLeft = beats } }
        mainClock.autoAdvance = false
        setContent {
            // The simulator's own Reduce Motion setting must not decide this test: the pulse is the subject.
            CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = false)) {
                TembraTheme {
                    RecordButton(state = RecordButtonState.CountIn, onRecord = {}, countInBeatsLeft = 4, telemetry = telemetry)
                }
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(200)
        val pulsing = middleRow()
        mainClock.advanceTimeBy(800)
        val resting = middleRow()
        assertTrue(differences(pulsing, resting) > 0, "the ring 200 ms after a beat differs from the rest state")
        beats = 3
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(200)
        val restarted = middleRow()
        assertTrue(differences(restarted, resting) > 0, "the engine's next beat restarts the ring")
        mainClock.advanceTimeBy(800)
        assertEquals(0, differences(middleRow(), resting), "the ring rests again after the pulse")
    }

    /** The colours of the button's middle row outside the 20 dp disc. */
    private fun ComposeUiTest.middleRow(): List<Color> {
        val pixels = onNodeWithTag(PerfAnchors.TRANSPORT_RECORD).captureToImage().toPixelMap()
        val y = pixels.height / 2
        val centre = pixels.width / 2
        val disc = with(density) { 11.dp.roundToPx() }
        return (0 until pixels.width).filter { kotlin.math.abs(it - centre) > disc }.map { pixels[it, y] }
    }

    private fun differences(a: List<Color>, b: List<Color>): Int = a.indices.count { i ->
        kotlin.math.abs(a[i].red - b[i].red) > 0.03f || kotlin.math.abs(a[i].green - b[i].green) > 0.03f || kotlin.math.abs(a[i].blue - b[i].blue) > 0.03f
    }
}
