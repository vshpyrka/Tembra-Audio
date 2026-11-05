package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tracksettings.GainSlider
import audio.tembra.ui.transport.MasterVolumeSlider
import audio.tembra.ui.tuner.NoiseGateSlider
import audio.tembra.ui.tuner.ScriptedTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wheel over a slider (feature 011, spec FR-014): one notch away from the user raises the value by the slider's
 * own step (the step its arrow keys take), one notch towards the user lowers it, on the gain, noise gate and master
 * volume sliders.
 */
@OptIn(ExperimentalTestApi::class)
class TembraSliderWheelTest {

    @Test
    fun masterVolumeStepsOnePercentPerNotch() = runComposeUiTest {
        var percent by mutableStateOf(50)
        setContent { TembraTheme { Box(Modifier.width(300.dp)) { MasterVolumeSlider(percent = percent, onVolume = { percent = it }) } } }
        onNodeWithTag("transport.master").performMouseInput { moveTo(center); scroll(-1f) }
        assertEquals(51, percent)
        onNodeWithTag("transport.master").performMouseInput { scroll(1f) }
        onNodeWithTag("transport.master").performMouseInput { scroll(1f) }
        assertEquals(49, percent)
    }

    @Test
    fun theNoiseGateStepsOneStepPerNotch() = runComposeUiTest {
        var gate by mutableStateOf(NoiseGate.Off)
        setContent {
            TembraTheme {
                Box(Modifier.width(300.dp)) {
                    NoiseGateSlider(gate = gate, onGate = { gate = it }, onGateSettled = {}, telemetry = ScriptedTelemetry(), listening = false, soundAboveGate = null)
                }
            }
        }
        onNodeWithTag("tuner.noiseGate").performMouseInput { moveTo(center); scroll(-1f) }
        assertEquals(1, gate.step)
        onNodeWithTag("tuner.noiseGate").performMouseInput { scroll(1f) }
        assertEquals(0, gate.step)
    }

    @Test
    fun theGainRisesWithANotchAwayAndFallsWithOneTowards() = runComposeUiTest {
        var gain by mutableStateOf(GainDb.Unity)
        setContent { TembraTheme { Box(Modifier.width(300.dp)) { GainSlider(gain = gain, onGain = { gain = it }, testTag = "gain") } } }
        onNodeWithTag("gain").performMouseInput { moveTo(center); scroll(-1f) }
        assertTrue(gain.value > 0f, "a notch away did not raise the gain: $gain")
        onNodeWithTag("gain").performMouseInput { scroll(1f) }
        onNodeWithTag("gain").performMouseInput { scroll(1f) }
        assertTrue(gain.value < 0f, "two notches towards did not lower it below unity: $gain")
    }
}
