package audio.tembra.ui.perf

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.perf.AudioHealth
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The stress journey's last tap: the button writes the engine's health through the writer under its anchor tag. */
@OptIn(ExperimentalTestApi::class)
class AudioHealthWriteButtonTest {

    @Test
    fun tapWritesTheScriptedHealth() = runComposeUiTest {
        val engine = FakeAudioEngine()
        engine.scriptAudioHealth(AudioHealth(callbacks = 7, lastMicros = 1, maxMicros = 2, overBudget = 0, xruns = 0, realtime = false))
        val written = mutableListOf<String>()
        setContent {
            // Its own frame counter: the shared one may carry frames other tests' sheets dropped under the frozen clock.
            TembraTheme { AudioHealthWriteButton(engine = engine, writer = { json -> written += json; "/tmp/engine-health.json" }, meterFrames = MeterFrameCounter()) }
        }
        onNodeWithTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH).assertContentDescriptionEquals("Write engine health")
        onNodeWithTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH).performClick()
        assertEquals(listOf(engineHealthJson(engine.audioHealth())), written)
    }

    /**
     * Since the shell lost its top bar (2026-10-16) the button sits over the screens' top end corner: it is no larger
     * than the smallest page margin (8 dp), so it never covers a control a journey taps (the tuner's By Ear item ends
     * 16 dp from the edge in the wide and Pocket forms).
     */
    @Test
    fun theButtonFitsInsideThePageMargin() = runComposeUiTest {
        var margin = 0.dp
        setContent {
            TembraTheme {
                margin = TembraTheme.spacing.sm
                AudioHealthWriteButton(engine = FakeAudioEngine(), writer = { "/tmp/engine-health.json" })
            }
        }
        val bounds = onNodeWithTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH).getUnclippedBoundsInRoot()
        assertTrue(bounds.width <= margin + 0.5.dp && bounds.height <= margin + 0.5.dp, "the button is ${bounds.width} × ${bounds.height}")
    }
}
