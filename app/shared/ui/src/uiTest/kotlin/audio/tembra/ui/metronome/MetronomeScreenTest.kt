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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
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
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stateless metronome screen driven by state (contracts/metronome-screen.md) in the Stacked layout at 360 dp:
 * User Story 1 (tempo, play, cells) and User Story 2 (signature and subdivision chips, cells, style pills, section labels).
 */
@OptIn(ExperimentalTestApi::class)
class MetronomeScreenTest {

    private val telemetry = ScriptedBeatTelemetry()
    private var toggles = 0
    private val tempos = mutableListOf<Tempo>()
    private val signatures = mutableListOf<TimeSignature>()
    private val subdivisions = mutableListOf<Subdivision>()
    private val cycled = mutableListOf<Int>()
    private val styles = mutableListOf<MetronomeStyle>()
    private val callbacks = MetronomeCallbacks.None.copy(
        onTogglePlayback = { toggles++ },
        onTempo = { tempos += it },
        onSignature = { signatures += it },
        onSubdivision = { subdivisions += it },
        onCycleBeat = { cycled += it },
        onStyle = { styles += it },
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
        onNodeWithTag("metronome.play.icon.PlayArrowFilled", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.play").performClick()
        settle()
        assertEquals(1, toggles)
        update(loaded.copy(playback = PlaybackState.Running))
        onNodeWithTag("metronome.play").assertContentDescriptionEquals("Stop metronome")
        onNodeWithTag("metronome.play.icon.PauseFilled", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.play.icon.PlayArrowFilled", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSampledBeatReachesTheCells() = runComposeUiTest {
        val update = setScreen()
        onNodeWithTag("metronome.cell.2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Click"))
        update(loaded.copy(playback = PlaybackState.Running, currentBeat = 1))
        onNodeWithTag("metronome.cell.2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Click, playing"))
        onNodeWithTag("metronome.cell.1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Accent"))
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

    // --- User Story 2 ------------------------------------------------------------------------------------------------

    private fun settingsFor(signature: TimeSignature, style: MetronomeStyle? = MetronomeStyle.Standard) = MetronomeSettings(
        signature = signature,
        pattern = MetronomeStyle.Standard.pattern(signature),
        style = style,
    )

    @Test
    fun theSixSignaturesAreTabsWithFourFourSelected() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.signature").assertExists()
        val ids = listOf("4-4", "3-4", "6-8", "2-4", "5-4", "7-8")
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertExists()
        onNodeWithTag("metronome.signature.4-4").assertIsSelected()
        for (id in ids.drop(1)) onNodeWithTag("metronome.signature.$id").assertIsNotSelected()
        // Six chips share one row at 360 dp, each keeping its 44 dp target.
        val bounds = ids.map { onNodeWithTag("metronome.signature.$it").getUnclippedBoundsInRoot() }
        assertEquals(1, bounds.map { it.top }.distinct().size, "the signature chips wrapped at 360 dp")
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun aSignatureChipSelectsItsSignature() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.signature.6-8").performClick()
        settle()
        assertEquals(listOf(TimeSignature.SixEight), signatures)
    }

    @Test
    fun theSubdivisionLabelsFollowTheBeatUnit() = runComposeUiTest {
        val update = setScreen()
        val tags = listOf("metronome.subdivision.1", "metronome.subdivision.2", "metronome.subdivision.4", "metronome.subdivision.3")
        tags.zip(listOf("1/4", "1/8", "1/16", "3let")).forEach { (tag, label) -> onNodeWithTag(tag).assertTextEquals(label) }
        onNodeWithTag("metronome.subdivision.1").assertIsSelected()
        update(loaded.copy(settings = settingsFor(TimeSignature.SixEight)))
        tags.zip(listOf("1/8", "1/16", "1/32", "3let")).forEach { (tag, label) -> onNodeWithTag(tag).assertTextEquals(label) }
        onNodeWithTag("metronome.subdivision.2").performClick()
        settle()
        assertEquals(listOf(Subdivision.Two), subdivisions)
    }

    @Test
    fun sevenEightWrapsItsSevenCellsIntoTwoRows() = runComposeUiTest {
        setScreen(loaded.copy(settings = settingsFor(TimeSignature.SevenEight)))
        val cells = (1..7).map { onNodeWithTag("metronome.cell.$it").getUnclippedBoundsInRoot() }
        onNodeWithTag("metronome.cell.8").assertDoesNotExist()
        assertEquals(2, cells.map { it.top }.distinct().size, "seven cells at 360 dp are not in two rows")
        for (n in 1..7) onNodeWithTag("metronome.cell.$n").assertWidthIsAtLeast(44.dp)
    }

    @Test
    fun aCellClickCyclesItsBeat() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.cell.2").performClick()
        settle()
        assertEquals(listOf(1), cycled)
    }

    @Test
    fun theStylePillsFollowTheStyle() = runComposeUiTest {
        val update = setScreen()
        onNodeWithTag("metronome.style").assertExists()
        onNodeWithTag("metronome.style.standard").assertIsSelected()
        onNodeWithTag("metronome.style.downbeat").assertIsNotSelected()
        onNodeWithTag("metronome.style.syncopated").assertIsNotSelected()
        onNodeWithTag("metronome.style.downbeat").performClick()
        settle()
        assertEquals(listOf(MetronomeStyle.Downbeat), styles)
        // The selected pill ignores a click (TembraPill reports every click; the row filters it).
        onNodeWithTag("metronome.style.standard").performClick()
        settle()
        assertEquals(listOf(MetronomeStyle.Downbeat), styles)
        update(loaded.copy(settings = settingsFor(TimeSignature.FourFour, style = null)))
        for (id in listOf("standard", "downbeat", "syncopated")) onNodeWithTag("metronome.style.$id").assertIsNotSelected()
    }

    @Test
    fun sectionLabelsAreDrawnUppercaseAndExposedInSentenceCase() = runComposeUiTest {
        setScreen()
        for ((drawn, exposed) in listOf("TIME SIGNATURE" to "Time signature", "RHYTHM SUBDIVISION" to "Rhythm subdivision", "STYLE" to "Style")) {
            onNode(hasText(drawn), useUnmergedTree = true).assertExists()
            onNode(hasContentDescription(exposed), useUnmergedTree = true).assertExists()
            onNode(hasText(exposed), useUnmergedTree = true).assertDoesNotExist()
        }
        onNode(hasText("Common & Complex") and hasAnyAncestor(hasTestTag("screen.metronome")), useUnmergedTree = true).assertExists()
    }
}
