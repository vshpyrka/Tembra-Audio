package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.approximately
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stateless metronome screen driven by state (contracts/metronome-screen.md) in the Stacked layout at 360 dp:
 * User Story 1 (tempo, play, cells), User Story 2 (signature and subdivision chips, cells, style pills, section labels),
 * User Story 3 (the tap card, its caption and flash), User Story 4 (the sound pill and its menu) and User Story 5 (the
 * import messages reach the snackbar host).
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
    private val taps = mutableListOf<Long>()
    private val sounds = mutableListOf<ClickSound>()
    private var primary = Color.Unspecified
    private var pixelDensity = 1f
    private lateinit var compositionScope: CoroutineScope
    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }
    private val callbacks = MetronomeCallbacks.None.copy(
        onTogglePlayback = { toggles++ },
        onTempo = { tempos += it },
        onSignature = { signatures += it },
        onSubdivision = { subdivisions += it },
        onCycleBeat = { cycled += it },
        onStyle = { styles += it },
        onTap = { taps += it },
        onSound = { sounds += it },
    )

    private val loaded = MetronomeUiState(loaded = true)

    private fun ComposeUiTest.setScreen(
        initial: MetronomeUiState = loaded,
        layout: MetronomeLayout = MetronomeLayout.Stacked,
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
    ): (MetronomeUiState) -> Unit {
        var state by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                pixelDensity = LocalDensity.current.density
                compositionScope = rememberCoroutineScope()
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    MetronomeScreenContent(
                        state = state,
                        callbacks = callbacks,
                        telemetry = telemetry,
                        layout = layout,
                        snackbarHostState = snackbarHostState,
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

    // --- User Story 3 ------------------------------------------------------------------------------------------------

    @Test
    fun theTapCardReadsTapTempoWithItsRestCaption() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.tap")
            .assertContentDescriptionEquals("Tap tempo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sync finger rhythm"))
        onNode(hasText("TAP TEMPO") and hasAnyAncestor(hasTestTag("metronome.tap")), useUnmergedTree = true).assertExists()
        onNode(hasText("Sync finger rhythm") and hasAnyAncestor(hasTestTag("metronome.tap")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aTapReportsItsFrameTime() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.tap").performClick()
        settle()
        assertEquals(1, taps.size)
        assertTrue(taps.single() > 0L, "the tap carried no frame time")
    }

    @Test
    fun theCaptionCountsTheTaps() = runComposeUiTest {
        setScreen(loaded.copy(tapCaption = TapCaption.Counting(3)))
        onNode(hasText("3 taps") and hasAnyAncestor(hasTestTag("metronome.tap")), useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.tap").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 taps"))
    }

    /** Beside the title, clear of the text: the card's own fill with the flash drawn over it. */
    private fun ComposeUiTest.tapFill(): Color {
        val image = onNodeWithTag("metronome.tap").captureToImage()
        return image.averageColorAt((12 * pixelDensity).roundToInt(), image.height / 2)
    }

    @Test
    fun aTapFlashesTheCardFor120Milliseconds() = runComposeUiTest {
        setScreen()
        val rest = tapFill()
        onNodeWithTag("metronome.tap").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        val flashed = tapFill()
        assertTrue(flashed.distanceTo(primary) < rest.distanceTo(primary) - 0.02f, "the tap did not flash: $rest -> $flashed")
        mainClock.advanceTimeBy(150)
        settle()
        val after = tapFill()
        assertTrue(after.approximately(rest), "the flash did not fade: $rest -> $after")
    }

    @Test
    fun theFlashIsStaticUnderReduceMotion() = runComposeUiTest(effectContext = zeroScale) {
        setScreen()
        val rest = tapFill()
        onNodeWithTag("metronome.tap").performClick()
        repeat(4) {
            mainClock.advanceTimeByFrame()
            val now = tapFill()
            assertTrue(now.approximately(rest), "the card flashed under reduce motion: $rest -> $now")
        }
        assertEquals(1, taps.size)
    }

    // --- User Story 4 ------------------------------------------------------------------------------------------------

    @Test
    fun theSoundPickerIsAPillUnderTheStyleRow() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.sound").performScrollTo().assertContentDescriptionEquals("Click sound, Classic Wood")
        onNodeWithTag("metronome.sound.classic-wood").assertDoesNotExist()
        val style = onNodeWithTag("metronome.style").getUnclippedBoundsInRoot()
        val sound = onNodeWithTag("metronome.sound").getUnclippedBoundsInRoot()
        assertTrue(sound.top >= style.bottom, "the sound pill (top ${sound.top}) is not under the style row (bottom ${style.bottom})")
        onNodeWithTag("metronome.sound").performClick()
        settle()
        onNodeWithTag("metronome.sound.digital-beep").performClick()
        settle()
        assertEquals(listOf<ClickSound>(ClickSound.BuiltIn(BuiltInClickSound.DigitalBeep)), sounds)
    }

    // --- User Story 5 ------------------------------------------------------------------------------------------------

    @Test
    fun anImportRefusalShowsItsMessageInTheSnackbar() = runComposeUiTest {
        val snackbar = SnackbarHostState()
        setScreen(snackbarHostState = snackbar)
        // What the wiring's effect does with a `MetronomeMessage` from the ViewModel.
        val shown = runOnIdle { compositionScope.launch { snackbar.showSnackbar(MetronomeCopy.of(MetronomeMessage.NotWav)) } }
        mainClock.advanceTimeBy(1_000)
        onNode(hasText("That file isn't a WAV file Tembra can read.")).assertIsDisplayed()
        shown.cancel()
    }
}
