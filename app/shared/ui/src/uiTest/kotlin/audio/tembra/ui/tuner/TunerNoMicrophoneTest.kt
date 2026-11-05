package audio.tembra.ui.tuner

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A desktop without a microphone (feature 011, spec FR-002, User Story 1): Auto Listen says so in place of the meter
 * and asks for nothing; By Ear plays every string as before.
 */
@OptIn(ExperimentalTestApi::class)
class TunerNoMicrophoneTest {

    private val telemetry = ScriptedTelemetry()
    private val noMicrophone = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Unavailable)

    private fun ComposeUiTest.setScreen(state: TunerUiState, callbacks: TunerCallbacks = TunerCallbacks.None, layout: TunerLayout = TunerLayout.Stacked) {
        mainClock.autoAdvance = false
        setContent { TembraTheme { TunerScreenContent(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout) } }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    @Test
    fun autoListenSaysNoMicrophoneIsAvailableAndAsksForNothing() = runComposeUiTest {
        var requests = 0
        setScreen(noMicrophone, TunerCallbacks.None.copy(onRequestMicrophone = { requests++ }))
        onNodeWithTag("tuner.noMicrophone").assertExists()
        onNode(
            hasText("No microphone is available on this device", substring = true) and hasAnyAncestor(hasTestTag("tuner.noMicrophone")),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithTag("tuner.denied").assertDoesNotExist()
        onNodeWithText("Allow microphone").assertDoesNotExist()
        onNodeWithTag("tuner.meter").assertDoesNotExist()
        onNodeWithTag("tuner.noiseGate").assertDoesNotExist()
        assertEquals(0, requests)
    }

    @Test
    fun theMessageShowsInTheLandscapeFormToo() = runComposeUiTest {
        setScreen(noMicrophone, layout = TunerLayout.Landscape)
        onNodeWithTag("tuner.noMicrophone").assertExists()
        onNodeWithTag("tuner.meter").assertDoesNotExist()
    }

    @Test
    fun byEarStillAuditionsEveryString() = runComposeUiTest {
        var auditioned: Int? = null
        setScreen(noMicrophone.copy(mode = TunerMode.ByEar), TunerCallbacks.None.copy(onAudition = { auditioned = it }))
        onNodeWithTag("tuner.noMicrophone").assertDoesNotExist()
        onNodeWithTag("tuner.card.VI.audition").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertEquals(0, auditioned, "string VI is the first card")
    }
}
