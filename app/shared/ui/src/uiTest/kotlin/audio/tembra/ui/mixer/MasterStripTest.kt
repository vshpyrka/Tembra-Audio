package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.designsystem.FaderDefaults
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.transport.TransportCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The master strip (feature 012, spec FR-011, FR-012; contracts/mixer-ui.md "MasterStrip"; T076): the "Master" label,
 * the percent fader (0 … 100 in steps of 1, Page 10, home 80) under `mixer.master.fader` with the "80 percent" state and
 * the "80%" readout, the summed meter and its latch, and no knob or toggles.
 */
@OptIn(ExperimentalTestApi::class)
class MasterStripTest {

    private val calls = mutableListOf<String>()
    private val master = mutableStateOf(MasterUi(80))
    private val callbacks = TimelineCallbacks.None.copy(
        onMasterVolume = { percent ->
            calls += "master($percent)"
            master.value = MasterUi(percent)
        },
        onMasterSettled = { calls += "settled" },
        onResetMasterClip = { calls += "resetMaster" },
    )

    private fun ComposeUiTest.setStrip() {
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = true)) {
                    val feed = MeterFeed(levels = MixSnapshot(), tick = rememberFrameTick(active = true), mirror = remember { MixerLevelMirror() }, inputTelemetry = InputTelemetry { _, _ -> })
                    Box(Modifier.height(400.dp)) {
                        MasterStrip(master = master.value, callbacks = callbacks, feed = feed)
                    }
                }
            }
        }
    }

    @Test
    fun theFrameHasTheLabelTheFaderTheMeterAndThePercentageAndNothingElse() = runComposeUiTest {
        setStrip()
        onNodeWithTag(MixerTestTags.MASTER).assertExists()
        onNodeWithText(MixerCopy.master).assertExists()
        val fader = onNodeWithTag(MixerTestTags.MASTER_FADER)
            .assertContentDescriptionEquals(MixerCopy.masterVolume)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, TransportCopy.masterVolumeState(80)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 0)))
            .getUnclippedBoundsInRoot()
        assertTrue(fader.height >= FaderDefaults.MinThrow, "the master fader's throw is ${fader.height}")
        val meter = onNodeWithTag(MixerTestTags.MASTER_METER).assertContentDescriptionEquals(MixerCopy.level).getUnclippedBoundsInRoot()
        assertTrue(meter.left >= fader.right, "the meter $meter is not beside the fader $fader")
        onNodeWithTag(MixerTestTags.MASTER_CLIP).assertContentDescriptionEquals(MixerCopy.clipIndicator).assertHasNoClickAction()
        onNodeWithText(TransportCopy.masterVolumePercent(80)).assertExists()
        onNodeWithContentDescription(MixerCopy.solo).assertDoesNotExist()
        onNodeWithContentDescription(MixerCopy.mute).assertDoesNotExist()
        onNodeWithContentDescription(MixerCopy.panDescription(MixerCopy.master)).assertDoesNotExist()
    }

    @Test
    fun theFaderStepsOnePercentPagesTenAndReturnsHomeAtEighty() = runComposeUiTest {
        setStrip()
        onNodeWithTag(MixerTestTags.MASTER_FADER).requestFocus()
        onNodeWithTag(MixerTestTags.MASTER_FADER).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag(MixerTestTags.MASTER_FADER).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(MixerTestTags.MASTER_FADER).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(MixerTestTags.MASTER_FADER).performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag(MixerTestTags.MASTER_FADER).performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(listOf("master(81)", "settled", "master(91)", "settled", "master(100)", "settled", "master(90)", "settled", "master(80)", "settled"), calls)
        onNodeWithText(TransportCopy.masterVolumePercent(80)).assertExists()
    }

    @Test
    fun aDragReachesTheCallbackWhileItLastsAndSettlesOnce() = runComposeUiTest {
        setStrip()
        onNodeWithTag(MixerTestTags.MASTER_FADER).performTouchInput { swipeUp() }
        waitForIdle()
        assertTrue(calls.count { it.startsWith("master(") } >= 1, "the drag reported no value: $calls")
        assertEquals(1, calls.count { it == "settled" }, "one gesture settles once: $calls")
        assertEquals("settled", calls.last())
        assertTrue(master.value.percent > 80, "an upward drag raised the master: ${master.value}")
    }
}
