package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import audio.tembra.ui.designsystem.averageColor
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Expanded-width aspect and pointer indifference of the meter (spec FR-023, FR-024; User Story 6). */
@OptIn(ExperimentalTestApi::class)
class TunerMeterLayoutTest {

    private val telemetry = ScriptedTelemetry()

    @Test
    fun expandedWidthKeepsAspect() = runComposeUiTest {
        setContent {
            TembraTheme {
                Box(Modifier.width(600.dp)) {
                    TunerMeter(telemetry = telemetry, reading = null, listening = false, maxWidth = 480.dp, testTag = "tuner.meter")
                }
            }
        }
        val bounds = onNodeWithTag("tuner.meter").getUnclippedBoundsInRoot()
        val root = onRoot().getUnclippedBoundsInRoot()
        // The cap applies where the window allows it; a phone-sized test window gives the meter its full width instead.
        val expected = minOf(480.dp, root.width)
        assertTrue(abs((bounds.width - expected).value) < 0.5f, "width ${bounds.width}, expected $expected")
        val arc = expected.value / 1.8f
        assertTrue(bounds.height.value > arc, "height ${bounds.height} leaves no room for the text rows")
        assertTrue(bounds.height.value < arc + 320f, "height ${bounds.height} is far more than the arc plus the text rows")
    }

    @Test
    fun hoverDoesNothing() = runComposeUiTest {
        setContent {
            TembraTheme {
                Box(Modifier.width(340.dp)) {
                    TunerMeter(telemetry = telemetry, reading = null, listening = false, maxWidth = 340.dp, testTag = "tuner.meter")
                }
            }
        }
        val before: Color = onNodeWithTag("tuner.meter").captureToImage().averageColor()
        onNodeWithTag("tuner.meter").performMouseInput { enter(center); moveTo(center) }
        waitForIdle()
        val after: Color = onNodeWithTag("tuner.meter").captureToImage().averageColor()
        assertTrue(abs(before.red - after.red) < 0.005f && abs(before.green - after.green) < 0.005f && abs(before.blue - after.blue) < 0.005f, "hover changed the meter: $before -> $after")
    }
}
