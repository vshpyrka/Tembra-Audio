package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The stateless metronome screen driven by state (contracts/metronome-screen.md): User Story 1 cases in the Stacked layout. */
@OptIn(ExperimentalTestApi::class)
class MetronomeScreenTest {

    private val telemetry = ScriptedBeatTelemetry()
    private var toggles = 0
    private val tempos = mutableListOf<Tempo>()
    private val callbacks = MetronomeCallbacks.None.copy(
        onTogglePlayback = { toggles++ },
        onTempo = { tempos += it },
    )

    private val loaded = MetronomeUiState(loaded = true)

    private fun ComposeUiTest.setScreen(initial: MetronomeUiState = loaded, layout: MetronomeLayout = MetronomeLayout.Stacked): (MetronomeUiState) -> Unit {
        var state by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    MetronomeScreenContent(
                        state = state,
                        callbacks = callbacks,
                        telemetry = telemetry,
                        layout = layout,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        settle()
        return { next -> state = next; settle() }
    }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    @Test
    fun rootAndControlsCarryTheirTags() = runComposeUiTest {
        setScreen()
        onNodeWithTag("screen.metronome").assertExists()
        for (tag in listOf("metronome.dial", "metronome.slider", "metronome.tap", "metronome.play", "metronome.cells", "metronome.dialCard")) {
            onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun theTapAndPlayRowIsPinnedBelowTheScrollingColumn() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.dialCard").assertExists()
        onNodeWithTag("metronome.tap").assertIsDisplayed()
        onNodeWithTag("metronome.play").assertIsDisplayed()
        val root = onNodeWithTag("screen.metronome").getUnclippedBoundsInRoot()
        val play = onNodeWithTag("metronome.play").getUnclippedBoundsInRoot()
        val tap = onNodeWithTag("metronome.tap").getUnclippedBoundsInRoot()
        assertTrue(abs((root.bottom - play.bottom).value) < 0.5f, "the play button's bottom ${play.bottom} is not the content bottom ${root.bottom}")
        assertTrue(abs((root.bottom - tap.bottom).value) < 0.5f, "the tap button's bottom ${tap.bottom} is not the content bottom ${root.bottom}")
    }

    @Test
    fun playTogglesAndFollowsThePlaybackState() = runComposeUiTest {
        val update = setScreen()
        onNodeWithTag("metronome.play").assertContentDescriptionEquals("Start metronome")
        onNodeWithTag("metronome.play.icon.PlayArrow", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.play").performClick()
        settle()
        assertEquals(1, toggles)
        update(loaded.copy(playback = PlaybackState.Running))
        onNodeWithTag("metronome.play").assertContentDescriptionEquals("Stop metronome")
        onNodeWithTag("metronome.play.icon.Pause", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.play.icon.PlayArrow", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSliderSetsTheTempo() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.slider").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        settle()
        assertEquals(Tempo(200), tempos.last())
    }

    @Test
    fun theReadoutFollowsTheTempo() = runComposeUiTest {
        setScreen(loaded.copy(settings = MetronomeSettings(tempo = Tempo(121))))
        onNode(hasText("121") and hasAnyAncestor(hasTestTag("metronome.dial"))).assertExists()
        onNode(hasText("Allegro (120-156)") and hasAnyAncestor(hasTestTag("metronome.dial"))).assertExists()
    }
}
