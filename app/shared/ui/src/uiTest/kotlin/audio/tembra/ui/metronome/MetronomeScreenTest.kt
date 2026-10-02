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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
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
import audio.tembra.ui.theme.TembraIcon
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
    private var measurer: TextMeasurer? = null
    private var typography: Typography? = null
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
                    // Measured under the same density (and font scale) as the screen.
                    measurer = rememberTextMeasurer()
                    typography = TembraTheme.typography
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
        setScreen(layout = MetronomeLayout.Wide, size = wideWindow)
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
        assertTrue(settings.width >= 240.dp - 0.5.dp, "the settings column is ${settings.width} wide")
        // The compact card (T150): the signature menu and the tiles, no chips.
        for (tag in listOf("metronome.bar", "metronome.signature", "metronome.subdivision", "metronome.cells", "metronome.style")) {
            assertTrue(settings.contains(bounds(tag)), "$tag (${bounds(tag)}) leaves the column $settings")
        }
        onNodeWithTag("metronome.signature").assertContentDescriptionEquals("Time signature, 4/4")
        onNodeWithTag("metronome.signature.4-4").assertDoesNotExist()
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
        assertTrue(bounds("metronome.dial").height >= 160.dp - 0.5.dp, "the dial is ${bounds("metronome.dial").height}")
        assertLandscapeBlock("892 × 330")
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
                pixelDensity = LocalDensity.current.density
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
        val layout = marking.textLayoutResult()
        assertEquals(1, layout.lineCount, "the marking \"$text\" wraps")
        // One line is not enough: with `softWrap = false` a name too wide even at the step-down floor would be clipped.
        assertTrue(!layout.didOverflowWidth, "the marking \"$text\" overflows its width (${layout.size})")
        assertTrue(!layout.isLineEllipsized(0), "the marking \"$text\" is ellipsised")
        val dial = bounds("metronome.dial")
        val drawn = (layout.size.width / pixelDensity).dp
        assertTrue(drawn <= dial.width + 0.5.dp, "the marking \"$text\" is $drawn wide, wider than the dial ${dial.width}")
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
     * FR-028 at the largest font in a phone held sideways (the Pixel's pane): the compact bar-shape card (T150) renders
     * whole in the settings column, which may scroll at this size; nothing there overlaps, the signature pill's label and
     * the tiles' glyphs stay inside their controls, and the dial block (tap and play under the dial card) holds.
     */
    @Test
    fun theLargestFontKeepsTheLandscapeCardWholeBesideTheDialBlock() = runComposeUiTest {
        val update = setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp), fontScale = 2f)
        mainClock.autoAdvance = true
        // The pills' labels whole at this size: the signature, the sound, and the style with its longest name too.
        assertPillLabelNotCut("metronome.signature", "4/4")
        assertPillLabelNotCut("metronome.sound", "Classic Wood")
        assertPillLabelNotCut("metronome.style", "Standard")
        update(loaded.copy(settings = MetronomeSettings(style = MetronomeStyle.Syncopated, pattern = MetronomeStyle.Syncopated.pattern(TimeSignature.FourFour))))
        assertPillLabelNotCut("metronome.style", "Syncopated")
        val settings = "metronome.settings"
        val subdivisions = listOf("1", "2", "4", "3")
        // The 892 dp host is wider than a phone held upright: bounds, not visibility (see assertWithin).
        val drawn = listOf(
            "metronome.signature" to "metronome.signature.label",
            "metronome.style" to "metronome.style.label",
            "metronome.sound" to "metronome.sound.label",
        ) + subdivisions.map { "metronome.subdivision.$it" to "metronome.subdivision.$it.icon.${quarterGlyphs.getValue(it)}" }
        for ((parent, tag) in drawn) {
            val node = onNodeWithTag(tag, useUnmergedTree = true)
            node.performScrollTo()
            assertWithin(node, parent, "$tag in $parent", onScreen = false)
        }
        val controls = listOf("metronome.signature", "metronome.style") +
            subdivisions.map { "metronome.subdivision.$it" } +
            (1..4).map { "metronome.cell.$it" } + "metronome.sound"
        val column = bounds(settings)
        val placed = controls.map { it to bounds(it) }
        for ((tag, rect) in placed) assertTrue(rect.left >= column.left - 0.5.dp && rect.right <= column.right + 0.5.dp, "$tag ($rect) leaves the column $column")
        for ((tag, rect) in placed) assertTrue(rect.width >= 44.dp - 0.5.dp && rect.height >= 44.dp - 0.5.dp, "$tag is ${rect.width} × ${rect.height}")
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            assertTrue(!placed[i].second.intersects(placed[j].second), "${placed[i].first} ${placed[i].second} overlaps ${placed[j].first} ${placed[j].second}")
        }
        assertLandscapeBlock("2× font")
        for (tag in listOf("metronome.tap.title", "metronome.tap.caption")) {
            assertWithin(onNodeWithTag(tag, useUnmergedTree = true), "metronome.tap", "$tag in metronome.tap", onScreen = false)
        }
        assertMarkingOnOneLine(onScreen = false)
    }

    /**
     * A pill's drawn label ([text], tagged `<pill>.label`, kept out of the accessibility tree so its layout is not
     * exposed) is not cut: the label node is at least as wide as the text measured at the pill's style and the current
     * font scale on one line, and it lies inside its pill.
     */
    private fun ComposeUiTest.assertPillLabelNotCut(pill: String, text: String) {
        val label = onNodeWithTag("$pill.label", useUnmergedTree = true)
        label.performScrollTo()
        val node = label.getUnclippedBoundsInRoot()
        val style = checkNotNull(typography).labelMedium
        val needed = (checkNotNull(measurer).measure(text, style, maxLines = 1, softWrap = false).size.width / pixelDensity).dp
        assertTrue(node.width >= needed - 0.5.dp, "\"$text\" in $pill is cut: drawn ${node.width}, needs $needed")
        assertTrue(bounds(pill).contains(node), "\"$text\" leaves $pill")
    }

    /**
     * I1: large fonts on the measured phone content areas held sideways. The column grows with the font but never takes
     * the room the pane needs for a 160 dp ring: the dial pane stays whole beside it (the column may scroll).
     */
    @Test
    fun theLargestFontKeepsTheDialPaneOnTheDeviceHosts() = runComposeUiTest {
        val show = setLandscapeForms(fontScale = 2f)
        for (host in listOf(DpSize(686.dp, 308.dp), DpSize(785.dp, 303.dp))) {
            show(host, loaded)
            val what = "2× font, host ${host.width} × ${host.height}"
            assertLandscapeBlock(what)
            val pane = bounds("screen.metronome").right - bounds("metronome.settings").right - 12.dp
            assertTrue(pane >= 160.dp + 16.dp - 0.5.dp, "$what: the pane is $pane wide")
        }
    }

    /** The quarter-note figures by tile id, as `SubdivisionGlyphs` maps them. */
    private val quarterGlyphs = mapOf(
        "1" to TembraIcon.NoteQuarter.name, "2" to TembraIcon.NoteEighthPair.name,
        "4" to TembraIcon.NoteSixteenthQuad.name, "3" to TembraIcon.NoteEighthTriplet.name,
    )

    // --- T150: phone landscape uses the compact card ---------------------------------------------------------------------

    /** Composes the Landscape screen once; the returned setter switches window and state. */
    private fun ComposeUiTest.setLandscapeForms(fontScale: Float? = null): (DpSize, MetronomeUiState) -> Unit {
        var form by mutableStateOf(DpSize(891.dp, 411.dp) to loaded)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                pixelDensity = LocalDensity.current.density
                val content: @Composable () -> Unit = {
                    Box(Modifier.requiredSize(form.first)) {
                        MetronomeScreenContent(
                            state = form.second,
                            callbacks = callbacks,
                            telemetry = telemetry,
                            layout = MetronomeLayout.Landscape,
                            snackbarHostState = remember { SnackbarHostState() },
                        )
                    }
                }
                if (fontScale == null) content() else CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { content() }
            }
        }
        settle()
        return { size, state -> form = size to state; settle() }
    }

    /**
     * A Pixel 6 Pro (891 × 411 dp) and an iPhone 17 (874 × 402 dp) held sideways at the default font: the settings
     * column (2⁄5 of the width, T152) holds the landscape card (see [assertLandscapeCard]) without scrolling; the dial
     * card and the transport row span the pane.
     */
    @Test
    fun landscapeShowsTheWholeCompactCardWithoutScrolling() = runComposeUiTest {
        val show = setLandscapeForms()
        for (window in listOf(DpSize(891.dp, 411.dp), DpSize(874.dp, 402.dp))) for (signature in listOf(TimeSignature.FourFour, TimeSignature.SevenEight)) {
            show(window, loaded.copy(settings = settingsFor(signature)))
            val what = "${window.width} × ${window.height}, ${signature.label}"
            val root = bounds("screen.metronome")
            val column = bounds("metronome.settings")
            // The 2:3 split: the column is 2⁄5 of the content width.
            assertTrue(abs((column.width - root.width * 2f / 5f).value) <= 1f, "$what: the column is ${column.width} of ${root.width}")
            assertLandscapeCard(what, beats = signature.beats)
            assertLandscapeBlock(what)
        }
    }

    /**
     * The landscape card (T152) at the default font: the signature and style menus share the first line, the four tiles
     * the second, the [beats] cells (44 dp bar boxes, no label text) one row when each keeps 44 dp and two otherwise,
     * then the sound pill, all inside the column and the screen; the column does not scroll.
     */
    private fun ComposeUiTest.assertLandscapeCard(what: String, beats: Int) {
        val root = bounds("screen.metronome")
        val column = bounds("metronome.settings")
        val scroll = onNodeWithTag("metronome.settings").fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertEquals(0f, scroll?.maxValue?.invoke() ?: 0f, "$what: the settings column scrolls")
        for (tag in listOf("metronome.bar", "metronome.signature", "metronome.style", "metronome.subdivision", "metronome.cells", "metronome.sound")) {
            val rect = bounds(tag)
            assertTrue(root.contains(rect) && column.contains(rect), "$what: $tag ($rect) is not inside the column $column and the screen $root")
        }
        val signature = bounds("metronome.signature")
        val style = bounds("metronome.style")
        assertTrue(signature.right <= style.left && signature.bottom > style.top && style.bottom > signature.top, "$what: the menus $signature and $style do not share a line")
        val tiles = listOf("1", "2", "4", "3").map { bounds("metronome.subdivision.$it") }
        assertEquals(1, tiles.map { it.top }.distinct().size, "$what: the tiles are not one line")
        assertTrue(tiles.first().top >= maxOf(signature.bottom, style.bottom) - 0.5.dp, "$what: the tiles are not under the menus")
        val card = bounds("metronome.bar")
        val inner = card.width - 16.dp
        val oneRow = (inner - 4.dp * (beats - 1)) / beats >= 44.dp
        val cells = (1..beats).map { bounds("metronome.cell.$it") }
        assertEquals(if (oneRow) 1 else 2, cells.map { it.top }.distinct().size, "$what: the cells are in ${cells.map { it.top }.distinct().size} rows")
        for ((n, cell) in cells.withIndex()) {
            assertTrue(cell.width >= 44.dp - 0.5.dp && cell.height >= 44.dp - 0.5.dp, "$what: cell ${n + 1} is ${cell.width} × ${cell.height}")
            onNodeWithTag("metronome.cell.${n + 1}.label", useUnmergedTree = true).assertDoesNotExist()
        }
        assertTrue(cells.first().top >= tiles.first().bottom - 0.5.dp, "$what: the cells are not under the tiles")
        // The level is still announced without a drawn label.
        onNodeWithTag("metronome.cell.1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Accent"))
    }

    /**
     * The real content areas of a phone held sideways (the window less the navigation rail, the header, the safe areas
     * and the page margin, measured on the devices, NOTES.md T152): Pixel 6 Pro 753 × 287 dp, iPhone 17 654 × 292 dp
     * (the hosts here add the 16 dp side and 8 dp top and bottom margins). The 2⁄5 column (301 and 262 dp) holds the
     * whole landscape card and the sound pill without scrolling at 4/4 and at 7/8, and the dial pane stays whole.
     */
    @Test
    fun landscapeOnTheDeviceHostsShowsTheWholeCardAndDialPane() = runComposeUiTest {
        val show = setLandscapeForms()
        for (host in listOf(DpSize(785.dp, 303.dp), DpSize(686.dp, 308.dp))) for (signature in listOf(TimeSignature.FourFour, TimeSignature.SevenEight)) {
            show(host, loaded.copy(settings = settingsFor(signature)))
            val what = "host ${host.width} × ${host.height}, ${signature.label}"
            val root = bounds("screen.metronome")
            val column = bounds("metronome.settings")
            assertTrue(abs((column.width - maxOf(root.width * 2f / 5f, 240.dp)).value) <= 1f, "$what: the column is ${column.width}")
            assertLandscapeCard(what, beats = signature.beats)
            assertLandscapeBlock(what)
        }
    }

    /**
     * T152: the dial card spans the dial pane (the 3⁄5 right of the settings column and its gap) with the ring inside it,
     * and tap (taking the rest of the row) and play sit under it across the pane; all inside the screen, the ring at
     * least 160 dp, the row 48–64 dp tall, nothing in the pane scrolls.
     */
    private fun ComposeUiTest.assertLandscapeBlock(what: String) {
        val root = bounds("screen.metronome")
        val pane = bounds("metronome.settings").right + 12.dp to root.right
        val card = bounds("metronome.dialCard")
        val tap = bounds("metronome.tap")
        val play = bounds("metronome.play")
        assertTrue(abs((card.left - pane.first).value) < 1f && abs((card.right - pane.second).value) < 1f, "$what: the dial card ($card) does not span the pane ${pane.first}..${pane.second}")
        for ((tag, rect) in listOf("metronome.dialCard" to card, "metronome.tap" to tap, "metronome.play" to play, "metronome.slider" to bounds("metronome.slider"))) {
            assertTrue(root.contains(rect), "$what: $tag ($rect) is outside the screen $root")
            onNode(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).assertDoesNotExist()
        }
        assertTrue(tap.top >= card.bottom - 0.5.dp && play.top >= card.bottom - 0.5.dp, "$what: tap ($tap) and play ($play) are not under the dial card ($card)")
        assertTrue(abs((tap.left - pane.first).value) < 1f && abs((play.right - pane.second).value) < 1f && play.left >= tap.right, "$what: tap ($tap) and play ($play) do not span the pane")
        for ((tag, rect) in listOf("metronome.tap" to tap, "metronome.play" to play)) {
            assertTrue(rect.height >= 48.dp - 0.5.dp && rect.height <= 64.dp + 0.5.dp, "$what: $tag is ${rect.height} tall")
        }
        val dial = bounds("metronome.dial")
        assertTrue(dial.height >= 160.dp - 0.5.dp, "$what: the ring is ${dial.height}")
        assertTrue(card.contains(dial), "$what: the dial leaves its card")
        assertTrue(abs(((dial.left + dial.right) / 2 - (card.left + card.right) / 2).value) < 1f, "$what: the ring is not centred in its card")
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
        // The tap title keeps its full size and wraps rather than shrinking (I2: only the landscape row steps it down).
        val title = onNodeWithTag("metronome.tap.title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleStyle = checkNotNull(typography).headlineSmall.copy(fontWeight = FontWeight.Bold)
        val line = (checkNotNull(measurer).measure("TAP TEMPO", titleStyle, maxLines = 1).size.height / pixelDensity).dp
        assertTrue(title.height >= line - 0.5.dp && title.height <= line * 2 + 0.5.dp, "the tap title is ${title.height} tall, one line is $line")
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
            // 17 in the landscape card (two menus for six chips and three pills), 19 in the portrait card, more in Wide.
            assertTrue(nodes.size >= 17, "$layout: only ${nodes.size} interactive nodes")
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

    /** A phone held sideways: the compact card first, then the dial block (the dial, the slider, tap and play under it). */
    @Test
    fun tabFollowsTheReadingOrderInTheLandscapeLayout() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp))
        val landscapeCard = listOf("metronome.style") + listOf("1", "2", "4", "3").map { "metronome.subdivision.$it" } + (1..4).map { "metronome.cell.$it" }
        assertTabOrder(from = "metronome.signature", expected = landscapeCard + "metronome.sound" + transportTabOrder)
    }

    /** I4: in the landscape card, Enter on the focused signature pill opens its menu (as in portrait). */
    @Test
    fun enterOpensTheSignatureMenuInTheLandscapeLayout() = runComposeUiTest {
        setScreen(layout = MetronomeLayout.Landscape, size = DpSize(892.dp, 330.dp))
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        onNodeWithTag("metronome.signature").requestFocus()
        settle()
        onNodeWithTag("metronome.signature").performKeyInput { pressKey(Key.Enter) }
        settle()
        onNodeWithTag("metronome.signature").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNodeWithTag("metronome.signature.7-8").assertExists()
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
