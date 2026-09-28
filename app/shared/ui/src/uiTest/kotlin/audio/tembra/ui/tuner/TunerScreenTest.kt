package audio.tembra.ui.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.AudioState
import audio.tembra.engine.TestSignals
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stateless tuner screen driven by state (contracts/tuner-screen.md): US1 cases. */
@OptIn(ExperimentalTestApi::class)
class TunerScreenTest {

    private val telemetry = ScriptedTelemetry()
    private var callbacks = TunerCallbacks.None

    private val listening = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, audio = AudioState.Listening)

    private fun ComposeUiTest.setScreen(initial: TunerUiState, layout: TunerLayout = TunerLayout.Stacked): (TunerUiState) -> Unit {
        var state by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                TunerScreenContent(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout)
            }
        }
        settle()
        return { next -> state = next; settle() }
    }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    @Test
    fun rootUsesTheShellTag() = runComposeUiTest {
        setScreen(listening)
        onNodeWithTag("screen.tuner").assertExists()
    }

    @Test
    fun autoListenShowsMeterAndPill() = runComposeUiTest {
        setScreen(listening)
        onNodeWithTag("tuner.autoListen").assertExists()
        onNodeWithTag("tuner.meter").assertExists()
        onNodeWithTag("tuner.pill").assertContentDescriptionEquals("Mic active")
        onNode(hasText("MIC ACTIVE") and hasAnyAncestor(hasTestTag("tuner.pill")), useUnmergedTree = true).assertExists()
        onNodeWithTag("tuner.byEar").assertDoesNotExist()
    }

    @Test
    fun testSignalPill() = runComposeUiTest {
        setScreen(listening.copy(testSignal = TestSignals.byId("e2-in-tune")!!.signal, microphone = MicrophonePermissionStatus.NotDetermined))
        onNodeWithTag("tuner.pill").assertContentDescriptionEquals("Test signal")
        onNodeWithTag("tuner.meter").assertExists()
    }

    @Test
    fun modeSelectorCallsBack() = runComposeUiTest {
        var selected: TunerMode? = null
        callbacks = TunerCallbacks.None.copy(onMode = { selected = it })
        setScreen(listening)
        onNodeWithTag("tuner.mode.autoListen").assertIsSelected()
        onNodeWithTag("tuner.mode.byEar").performClick()
        settle()
        assertEquals(TunerMode.ByEar, selected)
    }

    @Test
    fun idleHidesPill() = runComposeUiTest {
        val update = setScreen(listening.copy(audio = AudioState.Idle))
        onNodeWithTag("tuner.pill").assertDoesNotExist()
        update(listening)
        onNodeWithTag("tuner.pill").assertExists()
    }
}
