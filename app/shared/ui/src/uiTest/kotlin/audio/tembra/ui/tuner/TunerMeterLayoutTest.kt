package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
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
                    TunerMeter(telemetry = telemetry, reading = null, listening = false, testTag = "tuner.meter", modifier = Modifier.widthIn(max = 480.dp))
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

    /** The meter has no input surface at all, so no pointer or focus event can change it (spec FR-024). */
    @Test
    fun theMeterIsInert() = runComposeUiTest {
        setContent {
            TembraTheme {
                Box(Modifier.width(340.dp)) {
                    TunerMeter(telemetry = telemetry, reading = null, listening = false, testTag = "tuner.meter")
                }
            }
        }
        onNodeWithTag("tuner.meter")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }
}
