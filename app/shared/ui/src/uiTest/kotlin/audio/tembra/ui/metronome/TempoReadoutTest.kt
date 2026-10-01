package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.designsystem.textLayoutResult
import audio.tembra.ui.metronome.components.TempoReadout
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The readout's marking stays on one line (contracts/design-system-additions.md, ruling 2026-10-01): the full
 * "Moderato (108-120)" when the readout is wide enough, the name alone when it is not, never two lines.
 */
@OptIn(ExperimentalTestApi::class)
class TempoReadoutTest {

    private fun ComposeUiTest.setReadout(width: Dp): (Dp) -> Unit {
        var boxWidth by mutableStateOf(width)
        setContent {
            TembraTheme {
                Box(Modifier.width(boxWidth)) {
                    TempoReadout(tempo = Tempo(120), marking = TempoMarking.Moderato)
                }
            }
        }
        waitForIdle()
        return { next -> boxWidth = next; waitForIdle() }
    }

    private fun ComposeUiTest.assertOneLine(text: String) {
        val node = onNode(hasText(text), useUnmergedTree = true)
        node.assertExists()
        assertEquals(1, node.textLayoutResult().lineCount, "\"$text\" is not on one line")
    }

    @Test
    fun aWideReadoutShowsTheNameAndRangeOnOneLine() = runComposeUiTest {
        setReadout(240.dp)
        assertOneLine("Moderato (108-120)")
    }

    /** The width the readout gets between the steppers of a ring of about 200 dp (the iPhone 17 in portrait). */
    @Test
    fun aNarrowReadoutDropsTheRangeAndStaysOnOneLine() = runComposeUiTest {
        val resize = setReadout(240.dp)
        resize(96.dp)
        assertOneLine("Moderato")
        onNode(hasText("Moderato (108-120)"), useUnmergedTree = true).assertDoesNotExist()
        // Wide again: the range comes back.
        resize(240.dp)
        assertOneLine("Moderato (108-120)")
    }
}
