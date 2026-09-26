package audio.tembra.ui.tuner

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.TestSignals
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth
import kotlin.test.Test
import kotlin.test.assertTrue

/** The denied state that replaces the meter (spec FR-015; User Story 5). */
@OptIn(ExperimentalTestApi::class)
class TunerScreenDeniedTest {

    private val telemetry = ScriptedTelemetry()
    private var callbacks = TunerCallbacks.None
    private val denied = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.DeniedOpenSettings)

    private fun ComposeUiTest.setScreen(state: TunerUiState) {
        mainClock.autoAdvance = false
        setContent { TembraTheme { TunerScreenContent(state = state, callbacks = callbacks, telemetry = telemetry, windowWidth = WindowWidth.Compact) } }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    @Test
    fun deniedStateReplacesMeter() = runComposeUiTest {
        var requested = false
        callbacks = TunerCallbacks.None.copy(onRequestMicrophone = { requested = true })
        setScreen(denied)
        onNodeWithTag("tuner.denied").assertExists()
        onNode(hasText("Microphone needed") and hasAnyAncestor(hasTestTag("tuner.denied")), useUnmergedTree = true).assertExists()
        onNode(
            hasText("Tembra Audio listens to your instrument to show its tuning. Allow the microphone to use Auto Listen.") and hasAnyAncestor(hasTestTag("tuner.denied")),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithTag("tuner.denied.action").assert(hasText("Allow microphone"))
        onNodeWithTag("tuner.meter").assertDoesNotExist()
        onNodeWithTag("tuner.pill").assertDoesNotExist()
        onNodeWithTag("tuner.denied.action").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertTrue(requested)
    }

    @Test
    fun deniedStateIsAutoListenOnly() = runComposeUiTest {
        setScreen(denied.copy(mode = TunerMode.ByEar))
        onNodeWithTag("tuner.denied").assertDoesNotExist()
        onNodeWithTag("tuner.card.VI").assertExists()
    }

    @Test
    fun deniedStateNotShownWithTestSignal() = runComposeUiTest {
        setScreen(denied.copy(testSignal = TestSignals.byId("e2-in-tune")!!.signal))
        onNodeWithTag("tuner.meter").assertExists()
        onNodeWithTag("tuner.denied").assertDoesNotExist()
    }

    @Test
    fun deniedButtonIs44dp() = runComposeUiTest {
        setScreen(denied)
        onNodeWithTag("tuner.denied.action").assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
    }
}
