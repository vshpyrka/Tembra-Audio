package audio.tembra.ui.perf

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.engine.perf.AudioHealth
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stress journey's last tap: the button writes the engine's health through the writer under its anchor tag. */
@OptIn(ExperimentalTestApi::class)
class AudioHealthWriteButtonTest {

    @Test
    fun tapWritesTheScriptedHealth() = runComposeUiTest {
        val engine = FakeAudioEngine()
        engine.scriptAudioHealth(AudioHealth(callbacks = 7, lastMicros = 1, maxMicros = 2, overBudget = 0, xruns = 0, realtime = false))
        val written = mutableListOf<String>()
        setContent {
            TembraTheme { AudioHealthWriteButton(engine = engine, writer = { json -> written += json; "/tmp/engine-health.json" }) }
        }
        onNodeWithTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH).assertContentDescriptionEquals("Write engine health")
        onNodeWithTag(PerfAnchors.DEVTOOLS_WRITE_HEALTH).performClick()
        assertEquals(listOf(engineHealthJson(engine.audioHealth())), written)
    }
}
