package audio.tembra.ui.metronome

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.metronome.BeatLevel
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.TembraPill
import audio.tembra.ui.designsystem.approximately
import audio.tembra.ui.designsystem.assertDisplayedWithin
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.designsystem.largeFonts
import audio.tembra.ui.designsystem.textLayoutResult
import audio.tembra.ui.metronome.components.AccentCell
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private var secondary = Color.Unspecified
    private var pixelDensity = 1f
    private var inputModeManager: InputModeManager? = null
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

    /**
     * Composes the screen once per test. Without a [size] it is 360 dp wide and as tall as the window; with one it is
     * that size exactly (it may overflow a phone's window, so layout tests read bounds rather than visibility). A
     * [fontScale] replaces the system font scale, as the largest accessibility size would.
     */
    private fun ComposeUiTest.setScreen(
        initial: MetronomeUiState = loaded,
        layout: MetronomeLayout = MetronomeLayout.Stacked,
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
        size: DpSize? = null,
        fontScale: Float? = null,
    ): (MetronomeUiState) -> Unit {
        var state by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                primary = TembraTheme.colors.primary
                secondary = TembraTheme.colors.secondary
                pixelDensity = LocalDensity.current.density
                compositionScope = rememberCoroutineScope()
                val content: @Composable () -> Unit = {
                    Box(if (size != null) Modifier.requiredSize(size) else Modifier.width(360.dp).fillMaxHeight()) {
                        MetronomeScreenContent(
                            state = state,
                            callbacks = callbacks,
                            telemetry = telemetry,
                            layout = layout,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                }
                if (fontScale == null) content() else CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { content() }
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
        // The full "Allegro (120-156)" or, on a ring too small for the range, the name alone (one line either way).
        onNode(hasText("Allegro", substring = true) and hasAnyAncestor(hasTestTag("metronome.dial"))).assertExists()
    }

    // --- User Story 2 ------------------------------------------------------------------------------------------------

    private fun settingsFor(signature: TimeSignature, style: MetronomeStyle? = MetronomeStyle.Standard) = MetronomeSettings(
        signature = signature,
        pattern = MetronomeStyle.Standard.pattern(signature),
        style = style,
    )

    /** The compact layout's signature is a menu: the pill names it, the six entries open from it (design iteration D1). */
    @Test
    fun theCompactSignatureIsAMenuOfTheSix() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.signature").assertContentDescriptionEquals("Time signature, 4/4")
        onNodeWithTag("metronome.signature").performClick()
        settle()
        onNodeWithTag("metronome.signature.4-4").assertIsSelected()
        onNodeWithTag("metronome.signature.6-8").performClick()
        settle()
        assertEquals(listOf(TimeSignature.SixEight), signatures)
    }

    @Test
    fun theSixSignaturesAreTabsWithFourFourSelected() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
        onNodeWithTag("metronome.signature").assertExists()
        val ids = listOf("4-4", "3-4", "6-8", "2-4", "5-4", "7-8")
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertExists()
        onNodeWithTag("metronome.signature.4-4").assertIsSelected()
        for (id in ids.drop(1)) onNodeWithTag("metronome.signature.$id").assertIsNotSelected()
        // Six chips share one row in the expanded settings column, each keeping its 44 dp target.
        val bounds = ids.map { onNodeWithTag("metronome.signature.$it").getUnclippedBoundsInRoot() }
        assertEquals(1, bounds.map { it.top }.distinct().size, "the signature chips wrapped in the expanded column")
        for (id in ids) onNodeWithTag("metronome.signature.$id").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun aSignatureChipSelectsItsSignature() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = landscapeWindow)
        onNodeWithTag("metronome.signature.6-8").performClick()
        settle()
        assertEquals(listOf(TimeSignature.SixEight), signatures)
    }

    /** The compact tiles name their value to accessibility (the chips' text in the other layouts). */
    @Test
    fun theCompactSubdivisionTilesFollowTheBeatUnit() = runComposeUiTest {
        val update = setScreen()
        val tags = listOf("metronome.subdivision.1", "metronome.subdivision.2", "metronome.subdivision.4", "metronome.subdivision.3")
        tags.zip(listOf("1/4", "1/8", "1/16", "3let")).forEach { (tag, label) -> onNodeWithTag(tag).assertContentDescriptionEquals(label) }
        onNodeWithTag("metronome.subdivision.1").assertIsSelected()
        update(loaded.copy(settings = settingsFor(TimeSignature.SixEight)))
        tags.zip(listOf("1/8", "1/16", "1/32", "3let")).forEach { (tag, label) -> onNodeWithTag(tag).assertContentDescriptionEquals(label) }
        onNodeWithTag("metronome.subdivision.2").performClick()
        settle()
        assertEquals(listOf(Subdivision.Two), subdivisions)
    }

    @Test
    fun theSubdivisionLabelsFollowTheBeatUnit() = runComposeUiTest {
        val update = setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
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
    fun sevenEightWrapsItsSevenCellsIntoTwoRowsOnA360dpPhone() = runComposeUiTest {
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
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
        for ((drawn, exposed) in listOf("TIME SIGNATURE" to "Time signature", "RHYTHM SUBDIVISION" to "Rhythm subdivision", "STYLE" to "Style")) {
            onNode(hasText(drawn), useUnmergedTree = true).assertExists()
            onNode(hasContentDescription(exposed), useUnmergedTree = true).assertExists()
            onNode(hasText(exposed), useUnmergedTree = true).assertDoesNotExist()
        }
        onNode(hasText("Common & Complex") and hasAnyAncestor(hasTestTag("screen.metronome")), useUnmergedTree = true).assertExists()
    }

    /** The compact bar-shape card has no section headers but the style row's (design iteration D1). */
    @Test
    fun theCompactCardKeepsOnlyTheStyleLabel() = runComposeUiTest {
        setScreen()
        onNode(hasText("STYLE"), useUnmergedTree = true).assertExists()
        for (drawn in listOf("TIME SIGNATURE", "RHYTHM SUBDIVISION")) onNode(hasText(drawn), useUnmergedTree = true).assertDoesNotExist()
        // No drawn heading: the card's semantics name the group (contracts/metronome-screen.md).
        onNodeWithTag("metronome.bar").assertContentDescriptionEquals("Bar shape")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
        val card = bounds("metronome.bar")
        for (tag in listOf("metronome.signature", "metronome.subdivision", "metronome.cells", "metronome.style")) {
            assertTrue(card.contains(bounds(tag)), "$tag (${bounds(tag)}) is not in the bar-shape card $card")
        }
        // Row 1: the signature pill at the left, the tiles at the right, on one line.
        val pill = bounds("metronome.signature")
        val tiles = bounds("metronome.subdivision")
        assertTrue(pill.right <= tiles.left && pill.bottom > tiles.top && tiles.bottom > pill.top, "the pill $pill and the tiles $tiles do not share a row")
        assertTrue(bounds("metronome.cells").top >= tiles.bottom, "the cells are not under the first row")
        assertTrue(bounds("metronome.style").top >= bounds("metronome.cells").bottom, "the style row is not under the cells")
    }

    // --- User Story 3 ------------------------------------------------------------------------------------------------

    @Test
    fun theTapCardReadsTapTempoWithItsRestCaption() = runComposeUiTest {
        setScreen()
        onNodeWithTag("metronome.tap")
            .assertContentDescriptionEquals("Tap tempo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sync finger rhythm"))
        // The drawn title and caption are there, but only the description and the state are read.
        onNodeWithTag("metronome.tap.title", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.tap.caption", useUnmergedTree = true).assertExists()
        onNodeWithTag("metronome.tap").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
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
        onNodeWithTag("metronome.tap.caption", useUnmergedTree = true).assertExists()
        // The state names the tempo the taps set, so the live region announces it (spec FR-030).
        onNodeWithTag("metronome.tap").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 taps, 120 beats per minute"))
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

    // --- User Story 7: layouts (T128) ----------------------------------------------------------------------------------

    /** A tablet or desktop window in the expanded band. */
    private val wideWindow = DpSize(1024.dp, 768.dp)

    /** A phone held sideways (600 dp or wider, under 480 dp tall). */
    private val landscapeWindow = DpSize(900.dp, 400.dp)

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.intersects(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.contains(other: DpRect, tolerance: Dp = 0.5.dp): Boolean =
        other.left >= left - tolerance && other.right <= right + tolerance && other.top >= top - tolerance && other.bottom <= bottom + tolerance

    @Test
    fun wideHasASettingsColumnWithSoundTilesBesideTheDialAndTransport() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
        val settings = bounds("metronome.settings")
        assertTrue(settings.width >= 320.dp - 0.5.dp, "the settings column is ${settings.width} wide")
        // The picker is the tile grid: a tile per sound, no pill naming the current one.
        onNodeWithTag("metronome.sound.classic-wood").assertExists()
        onNode(hasContentDescription("Click sound, Classic Wood")).assertDoesNotExist()
        assertTrue(settings.contains(bounds("metronome.sound")), "the tiles are not in the settings column")
        onNode(hasText("CLICK TIMBRE") and hasAnyAncestor(hasTestTag("metronome.settings")), useUnmergedTree = true).assertExists()
        val dialCard = bounds("metronome.dialCard")
        assertTrue(dialCard.left >= settings.right, "the dial card $dialCard is not right of the settings $settings")
        for (tag in listOf("metronome.tap", "metronome.play")) {
            val control = bounds(tag)
            assertTrue(control.top >= dialCard.bottom, "$tag ($control) is not below the dial card $dialCard")
            assertTrue(control.left >= settings.right, "$tag is not in the right column")
        }
        val dial = bounds("metronome.dial")
        assertTrue(dial.width <= 420.dp + 0.5.dp, "the dial is ${dial.width} wide, over the expanded cap")
        assertTrue(abs(((dial.left + dial.right) / 2 - (dialCard.left + dialCard.right) / 2).value) < 1f, "the dial is not centred in its card")
    }

    @Test
    fun landscapeHasANarrowColumnAndTheWholeTransportWithoutScrolling() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = landscapeWindow)
        val settings = bounds("metronome.settings")
        assertTrue(settings.width >= 224.dp - 0.5.dp && settings.width <= 280.dp + 0.5.dp, "the settings column is ${settings.width} wide")
        val first = listOf("4-4", "3-4", "6-8").map { bounds("metronome.signature.$it") }
        val second = listOf("2-4", "5-4", "7-8").map { bounds("metronome.signature.$it") }
        for (chip in first + second) assertTrue(settings.contains(chip), "a signature chip $chip leaves the column $settings")
        assertEquals(1, first.map { it.top }.distinct().size, "the first three signatures are not one row")
        assertEquals(1, second.map { it.top }.distinct().size, "the last three signatures are not one row")
        assertTrue(second[0].top >= first[0].bottom, "the signatures are not two rows")
        val screen = bounds("screen.metronome")
        for (tag in listOf("metronome.dial", "metronome.slider", "metronome.tap", "metronome.play")) {
            val control = bounds(tag)
            assertTrue(screen.contains(control), "$tag ($control) is outside the window $screen")
            assertTrue(control.left >= settings.right, "$tag is not beside the settings column")
            onNode(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).assertDoesNotExist()
        }
        assertTrue(bounds("metronome.dial").height >= 160.dp - 0.5.dp, "the dial is under 160 dp")
    }

    /** A Pixel 6 Pro held sideways, less the status and top bars (quickstart §8): a pane under 320 dp tall. */
    @Test
    fun aPhoneHeldSidewaysKeepsTheTransportInViewWithAReadableDial() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp))
        val screen = bounds("screen.metronome")
        for (tag in listOf("metronome.dial", "metronome.slider", "metronome.tap", "metronome.play")) {
            assertTrue(screen.contains(bounds(tag)), "$tag (${bounds(tag)}) is outside the window $screen")
            onNode(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).assertDoesNotExist()
        }
        assertTrue(bounds("metronome.dial").height >= 200.dp, "the dial is ${bounds("metronome.dial").height}, too small for its readout")
        // Beside the dial card, play sits above tap (revised 2026-10-01).
        val play = bounds("metronome.play")
        val tap = bounds("metronome.tap")
        assertTrue(play.left >= bounds("metronome.dialCard").right, "play ($play) is not beside the dial card")
        assertTrue(play.bottom <= tap.top, "play ($play) is not above tap ($tap)")
    }

    // --- Design iteration D1: the compact card keeps the dial on a phone screen (T144) ---------------------------------

    /**
     * The screen's host on a Pixel 6 Pro in portrait: its 411 × 891 dp window less the status bar, the app header and
     * the navigation bar (measured on the device, NOTES.md "Design iteration D1").
     */
    private val pixelHost = DpSize(411.dp, 700.dp)

    /** The same on the iPhone 17 (402 × 874 dp less the safe areas, the header and the navigation bar). */
    private val iphoneHost = DpSize(402.dp, 652.dp)

    /** A 360 × 640 dp phone less the same chrome as the Pixel (191 dp): the dial cannot keep its size without scrolling. */
    private val smallPhoneHost = DpSize(360.dp, 449.dp)

    /** Composes the Stacked screen once (Android allows one `setContent` per test); the returned setter switches host and state. */
    private fun ComposeUiTest.setStackedForms(): (DpSize, MetronomeUiState) -> Unit {
        var form by mutableStateOf(pixelHost to loaded)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Box(Modifier.requiredSize(form.first)) {
                    MetronomeScreenContent(
                        state = form.second,
                        callbacks = callbacks,
                        telemetry = telemetry,
                        layout = MetronomeLayout.Stacked,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        settle()
        return { size, state -> form = size to state; settle() }
    }

    /** How far the Stacked column can scroll: 0 when everything fits. */
    private fun ComposeUiTest.scrollRange(): Float {
        val nodes = onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("screen.metronome"))).fetchSemanticsNodes()
        return nodes.maxOfOrNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f } ?: 0f
    }

    @Test
    fun theDialSliderTapAndPlayFitAPhoneWithoutScrolling() = runComposeUiTest {
        val show = setStackedForms()
        for (host in listOf(pixelHost, iphoneHost)) for (signature in listOf(TimeSignature.FourFour, TimeSignature.SevenEight)) {
            show(host, loaded.copy(settings = settingsFor(signature)))
            val what = "${host.width} × ${host.height}, ${signature.label}"
            val root = bounds("screen.metronome")
            for (tag in listOf("metronome.bar", "metronome.sound", "metronome.dialCard", "metronome.dial", "metronome.slider", "metronome.tap", "metronome.play")) {
                assertTrue(root.contains(bounds(tag)), "$what: $tag (${bounds(tag)}) is outside the screen $root")
            }
            assertEquals(0f, scrollRange(), "$what: the column scrolls")
            val dial = bounds("metronome.dial")
            assertTrue(dial.height >= 160.dp - 0.5.dp && dial.height <= 300.dp + 0.5.dp, "$what: the dial is ${dial.height}")
            assertTrue(bounds("metronome.dialCard").contains(bounds("metronome.slider")), "$what: the slider is not in the dial card")
            assertMarkingOnOneLine()
        }
    }

    @Test
    fun sevenEightKeepsItsCellsInOneRowOnAPhone() = runComposeUiTest {
        val show = setStackedForms()
        for (host in listOf(pixelHost, iphoneHost)) {
            show(host, loaded.copy(settings = settingsFor(TimeSignature.SevenEight)))
            val cells = (1..7).map { bounds("metronome.cell.$it") }
            assertEquals(1, cells.map { it.top }.distinct().size, "${host.width}: the seven cells are not one row: ${cells.map { it.top }}")
            for ((n, cell) in cells.withIndex()) {
                assertTrue(cell.width >= 44.dp - 0.5.dp && cell.height >= 44.dp - 0.5.dp, "${host.width}: cell ${n + 1} is ${cell.width} × ${cell.height}")
                assertTrue(abs((cell.width - cells[0].width).value) < 1f, "${host.width}: cell ${n + 1} is ${cell.width}, cell 1 ${cells[0].width}")
                // Too narrow for "ACCENT": the bar alone shows the level, no label is drawn.
                onNodeWithTag("metronome.cell.${n + 1}.label", useUnmergedTree = true).assertDoesNotExist()
            }
            assertTrue(bounds("metronome.bar").contains(bounds("metronome.cells")), "${host.width}: the cells leave the card")
        }
        show(pixelHost, loaded)
        for (n in 1..4) onNodeWithTag("metronome.cell.$n.label", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSmallPhoneScrollsTheColumnAndKeepsTheDialAt160dp() = runComposeUiTest {
        val show = setStackedForms()
        show(smallPhoneHost, loaded)
        assertTrue(scrollRange() > 0f, "the column does not scroll on a 360 × 640 dp phone")
        assertTrue(bounds("metronome.dial").height >= 160.dp - 0.5.dp, "the dial is ${bounds("metronome.dial").height}")
        val root = bounds("screen.metronome")
        for (tag in listOf("metronome.tap", "metronome.play")) assertTrue(root.contains(bounds(tag)), "$tag is not pinned on screen")
    }

    /**
     * The text [label] inside [parent], scrolled into view (except in the pinned tap card), displayed within its parent
     * and whole: no line dropped past the line limit and none ellipsised (hasVisualOverflow also flags a paragraph laid
     * out wider than the text it holds, which is not a cut).
     */
    private fun ComposeUiTest.assertWhole(parent: String, label: String, onScreen: Boolean = true) {
        val text = onNode(hasText(label) and hasAnyAncestor(hasTestTag(parent)), useUnmergedTree = true)
        if (parent != "metronome.tap") text.performScrollTo()
        assertWithin(text, parent, "\"$label\" in $parent", onScreen)
        val layout = text.textLayoutResult()
        val cut = layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        assertTrue(!cut, "\"$label\" in $parent is cut short at the largest font (${layout.lineCount} lines in ${layout.size})")
    }

    /** The dial's marking, "Moderato (108-120)" or the name alone where the range does not fit: inside the dial, one line. */
    private fun ComposeUiTest.assertMarkingOnOneLine(onScreen: Boolean = true) {
        val marking = onNode(hasText("Moderato", substring = true) and hasAnyAncestor(hasTestTag("metronome.dial")), useUnmergedTree = true)
        // The Stacked dial sits in the scrolling column; the landscape pane does not scroll.
        if (onScreen) marking.performScrollTo()
        assertWithin(marking, "metronome.dial", "the marking", onScreen)
        val text = marking.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue(text == "Moderato (108-120)" || text == "Moderato", "the marking reads \"$text\"")
        assertEquals(1, marking.textLayoutResult().lineCount, "the marking \"$text\" wraps")
    }

    /**
     * Inside [parent]'s bounds; [onScreen] also asserts it is displayed. A host wider than the device's window (the
     * 892 dp landscape host on a phone held upright) is laid out in full but partly outside the window, so those
     * cases read bounds only, as the other landscape layout tests do.
     */
    private fun ComposeUiTest.assertWithin(node: SemanticsNodeInteraction, parent: String, what: String, onScreen: Boolean) {
        val container = bounds(parent)
        if (onScreen) {
            node.assertDisplayedWithin(container, what)
        } else {
            val rect = node.getUnclippedBoundsInRoot()
            assertTrue(container.contains(rect), "$what ($rect) leaves $parent ($container)")
        }
    }

    /**
     * FR-028 at the largest font in a phone held sideways (the Pixel's pane): the section labels and the chips, with their
     * note glyphs, stay whole in the settings column, nothing there overlaps, and play stays above tap beside the dial.
     */
    @Test
    fun theLargestFontKeepsTheLandscapeChipsWholeWithPlayAboveTap() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp), fontScale = 2f)
        mainClock.autoAdvance = true
        val settings = "metronome.settings"
        val signatures = listOf("4-4", "3-4", "6-8", "2-4", "5-4", "7-8")
        val subdivisions = listOf("1" to "1/4", "2" to "1/8", "4" to "1/16", "3" to "3let")
        val labels = listOf(settings to "TIME SIGNATURE", settings to "Common & Complex", settings to "RHYTHM SUBDIVISION") +
            signatures.map { "metronome.signature.$it" to it.replace('-', '/') } +
            subdivisions.map { (id, label) -> "metronome.subdivision.$id" to label } +
            listOf("metronome.style.standard" to "Standard", "metronome.style.downbeat" to "Downbeat", "metronome.style.syncopated" to "Syncopated")
        // The 892 dp host is wider than a phone held upright: bounds, not visibility (see assertWithin).
        for ((parent, label) in labels) assertWhole(parent, label, onScreen = false)
        for ((id, _) in subdivisions) {
            val glyph = onNodeWithTag("metronome.subdivision.$id.icon", useUnmergedTree = true)
            glyph.performScrollTo()
            assertWithin(glyph, "metronome.subdivision.$id", "the note glyph of subdivision $id", onScreen = false)
        }
        val controls = signatures.map { "metronome.signature.$it" } +
            subdivisions.map { "metronome.subdivision.${it.first}" } +
            (1..4).map { "metronome.cell.$it" } +
            listOf("standard", "downbeat", "syncopated").map { "metronome.style.$it" } + "metronome.sound"
        val column = bounds(settings)
        val placed = controls.map { it to bounds(it) }
        for ((tag, rect) in placed) assertTrue(rect.left >= column.left - 0.5.dp && rect.right <= column.right + 0.5.dp, "$tag ($rect) leaves the column $column")
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            assertTrue(!placed[i].second.intersects(placed[j].second), "${placed[i].first} ${placed[i].second} overlaps ${placed[j].first} ${placed[j].second}")
        }
        val screen = bounds("screen.metronome")
        for (tag in listOf("metronome.dial", "metronome.slider", "metronome.tap", "metronome.play")) {
            assertTrue(screen.contains(bounds(tag)), "$tag (${bounds(tag)}) is outside the window $screen")
        }
        assertTrue(bounds("metronome.play").bottom <= bounds("metronome.tap").top, "play (${bounds("metronome.play")}) is not above tap (${bounds("metronome.tap")})")
        for (tag in listOf("metronome.tap.title", "metronome.tap.caption")) {
            assertWithin(onNodeWithTag(tag, useUnmergedTree = true), "metronome.tap", "$tag in metronome.tap", onScreen = false)
        }
        assertMarkingOnOneLine(onScreen = false)
    }

    @Test
    fun theLargestFontKeepsEveryLabelWholeAndNothingOverlaps() = runComposeUiTest {
        setScreen(fontScale = 2f)
        // Scrolling a label into view animates; nothing else runs while stopped, so the clock may run by itself.
        mainClock.autoAdvance = true
        val labels = listOf(
            "metronome.style.standard" to "Standard", "metronome.style.downbeat" to "Downbeat", "metronome.style.syncopated" to "Syncopated",
            "metronome.dial" to "120", "metronome.dial" to "BPM",
        )
        for ((parent, label) in labels) assertWhole(parent, label)
        assertMarkingOnOneLine()
        // The drawn labels kept out of the accessibility tree (read through their control's description or state):
        // found by tag, each inside its control.
        val drawn = listOf(
            "metronome.signature" to "metronome.signature.label",
            "metronome.sound" to "metronome.sound.label",
            "metronome.tap" to "metronome.tap.title", "metronome.tap" to "metronome.tap.caption",
        )
        for ((parent, tag) in drawn) {
            val text = onNodeWithTag(tag, useUnmergedTree = true)
            if (parent != "metronome.tap") text.performScrollTo()
            text.assertDisplayedWithin(bounds(parent), "$tag in $parent")
        }
        // At this size even 4/4 cells are narrower than "ACCENT": the one row keeps 44 dp cells and the bars alone show
        // the levels rather than wrapping or cutting the labels (design iteration D1).
        for (n in 1..4) onNodeWithTag("metronome.cell.$n.label", useUnmergedTree = true).assertDoesNotExist()
        val controls = listOf("metronome.signature") +
            listOf("1", "2", "4", "3").map { "metronome.subdivision.$it" } +
            (1..4).map { "metronome.cell.$it" } +
            listOf("standard", "downbeat", "syncopated").map { "metronome.style.$it" } +
            listOf("metronome.sound", "metronome.dial", "metronome.slider", "metronome.tap", "metronome.play")
        // The scrolling column's controls against one another, and the pinned tap and play row against each other (a
        // control scrolled under the pinned row is clipped by the column, not overlapping).
        val pinned = setOf("metronome.tap", "metronome.play")
        val placed = controls.map { it to bounds(it) }
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            if ((placed[i].first in pinned) != (placed[j].first in pinned)) continue
            assertTrue(!placed[i].second.intersects(placed[j].second), "${placed[i].first} ${placed[i].second} overlaps ${placed[j].first} ${placed[j].second}")
        }
    }

    @Test
    fun everyTargetIsAtLeast44dpInEveryLayout() = runComposeUiTest {
        // Composed once (Android allows one setContent per test), then switched through the three layouts.
        var form by mutableStateOf(MetronomeLayout.Stacked to DpSize(360.dp, 800.dp))
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Box(Modifier.requiredSize(form.second)) {
                    MetronomeScreenContent(
                        state = loaded,
                        callbacks = callbacks,
                        telemetry = telemetry,
                        layout = form.first,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        for ((layout, size) in listOf(MetronomeLayout.Stacked to DpSize(360.dp, 800.dp), MetronomeLayout.Wide to wideWindow, MetronomeLayout.Landscape to landscapeWindow)) {
            form = layout to size
            settle()
            val interactive = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) or SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
            val nodes = onAllNodes(interactive).fetchSemanticsNodes()
            // 19 in the compact card's form (one signature menu instead of six chips), more in the others.
            assertTrue(nodes.size >= 19, "$layout: only ${nodes.size} interactive nodes")
            for (node in nodes) {
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                assertTrue(tag != null, "$layout: an interactive node has no test tag: ${node.config}")
                val target = bounds(tag)
                assertTrue(target.width >= 44.dp - 0.5.dp && target.height >= 44.dp - 0.5.dp, "$layout: $tag is ${target.width} × ${target.height}")
            }
        }
    }

    // --- User Story 7: keyboard and pointer (T129) -------------------------------------------------------------------

    @Test
    fun tabFollowsTheReadingOrder() = runComposeUiTest {
        setScreen()
        assertTabOrder(from = "metronome.signature", expected = compactSettingsTabOrder + "metronome.sound" + transportTabOrder)
    }

    /** The expanded form: the settings column (with the sound tiles row by row), then the dial card and the transport card. */
    @Test
    fun tabFollowsTheReadingOrderInTheWideLayout() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
        val tiles = listOf("classic-wood", "808-rim", "digital-beep", "cowbell", "mechanical", "custom").map { "metronome.sound.$it" }
        assertTabOrder(settingsTabOrder + tiles + transportTabOrder)
    }

    /** A phone held sideways, play over tap beside the dial: the column first, then the dial, the slider, play and tap. */
    @Test
    fun tabFollowsTheReadingOrderInTheLandscapeLayout() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp))
        assertTabOrder(settingsTabOrder + "metronome.sound" + transportTabOrder.dropLast(2) + "metronome.play" + "metronome.tap")
    }

    /** From [from] (the 4/4 chip by default), each Tab moves keyboard focus to the next of [expected]. */
    private fun ComposeUiTest.assertTabOrder(expected: List<String>, from: String = "metronome.signature.4-4") {
        if (!enterKeyboardMode(inputModeManager)) return
        onNodeWithTag(from).requestFocus()
        settle()
        onNodeWithTag(from).assertIsFocused()
        for (tag in expected) {
            onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            settle()
            onNodeWithTag(tag).assertIsFocused()
        }
    }

    /** The settings after the 4/4 chip, up to the sound picker, in every layout. */
    private val settingsTabOrder = listOf("3-4", "6-8", "2-4", "5-4", "7-8").map { "metronome.signature.$it" } +
        listOf("1", "2", "4", "3").map { "metronome.subdivision.$it" } +
        (1..4).map { "metronome.cell.$it" } +
        listOf("standard", "downbeat", "syncopated").map { "metronome.style.$it" }

    /** The compact card after the signature menu, up to the sound picker: the tiles, the cells and the style pills. */
    private val compactSettingsTabOrder = listOf("1", "2", "4", "3").map { "metronome.subdivision.$it" } +
        (1..4).map { "metronome.cell.$it" } +
        listOf("standard", "downbeat", "syncopated").map { "metronome.style.$it" }

    /** The dial card and the transport (tap, then play, as the row reads). */
    private val transportTabOrder =
        listOf("metronome.dial.down", "metronome.dial", "metronome.dial.up", "metronome.slider", "metronome.tap", "metronome.play")

    @Test
    fun theFocusedSliderTakesTheArrowAndPageKeys() = runComposeUiTest {
        setScreen()
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.slider").requestFocus()
        settle()
        onNodeWithTag("metronome.slider").performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        assertEquals(Tempo(121), tempos.last())
        onNodeWithTag("metronome.slider").performKeyInput { pressKey(Key.PageDown) }
        settle()
        assertEquals(Tempo(110), tempos.last())
    }

    @Test
    fun enterSelectsTheFocusedChip() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.signature.6-8").requestFocus()
        settle()
        onNodeWithTag("metronome.signature.6-8").performKeyInput { pressKey(Key.Enter) }
        settle()
        assertEquals(listOf(TimeSignature.SixEight), signatures)
    }

    /** Pixels that differ between two captures of the same node. */
    private fun changedPixels(before: ImageBitmap, after: ImageBitmap): Int {
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) for (x in 0 until minOf(a.width, b.width)) {
            if (a[x, y].distanceTo(b[x, y]) > 0.03f) count++
        }
        return count
    }

    @Test
    fun hoverChangesTheColoursOfACellAPillAndThePlayButton() = runComposeUiTest {
        val cell = MutableInteractionSource()
        val pill = MutableInteractionSource()
        val play = MutableInteractionSource()
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Column {
                    Box(Modifier.width(80.dp)) {
                        AccentCell(index = 2, level = BeatLevel.Click, lit = { false }, playing = false, onClick = {}, interactionSource = cell, testTag = "cell")
                    }
                    TembraPill(text = "Downbeat", selected = false, onClick = {}, interactionSource = pill, testTag = "pill")
                    PlayPauseButton(running = false, onToggle = {}, interactionSource = play, testTag = "play")
                }
            }
        }
        settle()
        val targets = listOf("cell" to cell, "pill" to pill, "play" to play)
        val rest = targets.associate { (tag, _) -> tag to onNodeWithTag(tag).captureToImage() }
        runOnIdle { for ((_, source) in targets) source.tryEmit(HoverInteraction.Enter()) }
        settle()
        for ((tag, _) in targets) {
            val changed = changedPixels(rest.getValue(tag), onNodeWithTag(tag).captureToImage())
            assertTrue(changed > 20, "hover did not change the $tag's colours ($changed pixels)")
        }
    }

    @Test
    fun thePlayButtonsTooltipIsItsDescription() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent {
            TembraTheme {
                Box(Modifier.width(200.dp).height(200.dp), contentAlignment = Alignment.Center) {
                    PlayPauseButton(running = false, onToggle = {}, interactionSource = source)
                }
            }
        }
        waitForIdle()
        onNode(hasText("Start metronome")).assertDoesNotExist()
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
        waitForIdle()
        val description = onNodeWithTag("metronome.play").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        onNode(hasText(description)).assertExists()
    }

    // --- User Story 7: semantics and reduce motion (T130) ------------------------------------------------------------

    @Test
    fun theControlsAnnounceWhatTheyAre() = runComposeUiTest {
        val update = setScreen()
        // Compose has no slider role: the range and the progress action are what make TalkBack and VoiceOver treat the
        // node as an adjustable slider, as Material's own Slider does.
        onNodeWithTag("metronome.dial")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "120 beats per minute, Moderato"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        onNodeWithTag("metronome.cell.3")
            .assertContentDescriptionEquals("Beat 3")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Click"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        // The slider announces the tempo and its name as the dial does (spec FR-030), not Material's "120.0".
        onNodeWithTag("metronome.slider").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "120 beats per minute, Moderato"))
        onNodeWithTag("metronome.play").assertContentDescriptionEquals("Start metronome")
        onNodeWithTag("metronome.sound").performScrollTo().assertContentDescriptionEquals("Click sound, Classic Wood")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        for (tag in listOf("metronome.subdivision.1", "metronome.style.standard")) {
            onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        }
        onNodeWithTag("metronome.signature")
            .assertContentDescriptionEquals("Time signature, 4/4")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        onNodeWithTag("metronome.dial.down").assertContentDescriptionEquals("Decrease tempo")
        onNodeWithTag("metronome.dial.up").assertContentDescriptionEquals("Increase tempo")
        update(loaded.copy(playback = PlaybackState.Running, currentBeat = 2))
        onNodeWithTag("metronome.cell.3").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Click, playing"))
        onNodeWithTag("metronome.play").assertContentDescriptionEquals("Stop metronome")
    }

    @Test
    fun theTapButtonAnnouncesTheTempoItSet() = runComposeUiTest {
        val update = setScreen()
        onNodeWithTag("metronome.tap").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        update(loaded.copy(settings = MetronomeSettings(tempo = Tempo(124)), tapCaption = TapCaption.Counting(3)))
        onNodeWithTag("metronome.tap")
            .assertContentDescriptionEquals("Tap tempo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 taps, 124 beats per minute"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    /** The screen's pixels [above] dp over the play button's top edge, at its centre: only the play glow tints them. */
    private fun ComposeUiTest.overThePlayButton(above: Dp = 4.dp): Color {
        val root = bounds("screen.metronome")
        val play = bounds("metronome.play")
        val image = onNodeWithTag("screen.metronome").captureToImage()
        val x = (((play.left + play.right) / 2 - root.left).value * pixelDensity).roundToInt()
        val y = ((play.top - above - root.top).value * pixelDensity).roundToInt()
        return image.averageColorAt(x, y)
    }

    /** Beside the bar of beat [beat], in its box's padding at mid-height: only the Accent glow tints it. */
    private fun ComposeUiTest.besideTheBar(beat: Int): Color {
        val image = onNodeWithTag("metronome.cell.$beat.bar", useUnmergedTree = true).captureToImage()
        // Between the 2 dp lit outline and the bar, which starts after the 4 dp padding.
        return image.averageColorAt((MetronomeDefaults.BarBoxPadding.value * 0.75f * pixelDensity).roundToInt(), image.height / 2)
    }

    /** The middle of the top edge of beat [beat]'s bar box, where the lit outline is drawn. */
    private fun ComposeUiTest.litEdge(beat: Int): Color {
        val image = onNodeWithTag("metronome.cell.$beat.bar", useUnmergedTree = true).captureToImage()
        return image.averageColorAt(image.width / 2, 1)
    }

    @Test
    fun reduceMotionStillsTheGlowsButStillLightsTheBeat() = runComposeUiTest(effectContext = zeroScale) {
        telemetry.lit = -1
        setScreen(loaded.copy(playback = PlaybackState.Running))
        val play = overThePlayButton()
        val accent = besideTheBar(1)
        val unlit = litEdge(1)
        // Beat 1 (the Accent) lights: without reduce motion its glow would brighten and the play glow keeps breathing.
        telemetry.lit = 0
        repeat(62) { mainClock.advanceTimeByFrame() }
        val playLater = overThePlayButton()
        val accentLater = besideTheBar(1)
        val lit = litEdge(1)
        assertTrue(play.approximately(playLater, 0.01f) && abs(play.alpha - playLater.alpha) < 0.01f, "the play glow moved under reduce motion: $play -> $playLater")
        assertTrue(accent.approximately(accentLater, 0.01f) && abs(accent.alpha - accentLater.alpha) < 0.01f, "the accent glow moved under reduce motion: $accent -> $accentLater")
        assertTrue(lit.distanceTo(secondary) < unlit.distanceTo(secondary) - 0.1f, "beat 1 did not light under reduce motion: $unlit -> $lit")
    }
}
