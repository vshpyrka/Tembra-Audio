package audio.tembra.ui.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.GainDb
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.GAIN_DESCRIPTION
import audio.tembra.ui.timeline.components.GainReadout
import audio.tembra.ui.timeline.components.SILENCE_DB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gain readout (spec FR-023; T092): readout text, drag, arrows, page keys, double tap, silence, range semantics. */
@OptIn(ExperimentalTestApi::class)
class GainReadoutTest {

    private val changes = mutableListOf<GainDb>()

    private fun ComposeUiTest.setReadout(initial: GainDb) {
        setContent {
            TembraTheme {
                var gain by remember { mutableStateOf(initial) }
                GainReadout(gain = gain, onGain = { gain = it; changes += it }, testTag = "gain")
            }
        }
    }

    @Test
    fun readsTheValueWithRangeSemantics() = runComposeUiTest {
        setReadout(GainDb(-2.4f))
        onNodeWithTag("gain")
            .assertContentDescriptionEquals(GAIN_DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−2.4dB"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(-2.4f, SILENCE_DB..GainDb.MAX, 0)))
    }

    @Test
    fun arrowsMoveHalfADecibelAndPageKeysSix() = runComposeUiTest {
        setReadout(GainDb.Unity)
        onNodeWithTag("gain").requestFocus()
        onNodeWithTag("gain").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag("gain").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("gain").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("gain").performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag("gain").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(listOf(0.5f, 0f, -0.5f, 5.5f, -0.5f), changes.map { it.value })
    }

    @Test
    fun theMaximumIsSixDecibels() = runComposeUiTest {
        setReadout(GainDb(5f))
        onNodeWithTag("gain").requestFocus()
        onNodeWithTag("gain").performKeyInput { pressKey(Key.PageUp) }
        assertEquals(listOf(6f), changes.map { it.value })
        onNodeWithTag("gain").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "+6.0dB"))
    }

    @Test
    fun aDoubleTapReturnsToZero() = runComposeUiTest {
        setReadout(GainDb(-3f))
        onNodeWithTag("gain").performTouchInput { doubleClick() }
        assertEquals(listOf(GainDb.Unity), changes)
    }

    @Test
    fun draggingUpRaisesTheGain() = runComposeUiTest {
        setReadout(GainDb(-6f))
        onNodeWithTag("gain").performTouchInput { swipeUp() }
        assertTrue(changes.isNotEmpty() && changes.last().value > -6f, "the drag did not raise the gain: $changes")
    }

    @Test
    fun belowSixtyDecibelsReadsMinusInfinity() = runComposeUiTest {
        setReadout(GainDb(-58f))
        onNodeWithTag("gain").requestFocus()
        onNodeWithTag("gain").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(listOf(GainDb.Silence), changes)
        onNodeWithTag("gain").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−∞"))
    }

    @Test
    fun setProgressAdjustsInHalfDecibels() = runComposeUiTest {
        setReadout(GainDb.Unity)
        onNodeWithTag("gain").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(-2.3f) }
        assertEquals(listOf(-2.5f), changes.map { it.value })
    }
}
