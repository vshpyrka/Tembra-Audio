package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The metronome above and below a tabletop fold (feature 005, spec FR-005, FR-010; contracts/tabletop-layout.md): only
 * the dial and the accent cells above it, every other control below it. Driven with `layout = Tabletop` and no fold,
 * so the regions split the host in half.
 */
@OptIn(ExperimentalTestApi::class)
class MetronomeTabletopLayoutTest {

    private val telemetry = ScriptedBeatTelemetry()
    private var toggles = 0
    private val cycled = mutableListOf<Int>()
    private val callbacks = MetronomeCallbacks.None.copy(onTogglePlayback = { toggles++ }, onCycleBeat = { cycled += it })

    /** The Pixel 9 Pro Fold's inner screen half-opened, less the status bar, the shell's former top bar and the rail. */
    private val foldContent = DpSize(800.dp, 720.dp)

    private val loaded = MetronomeUiState(loaded = true)
    private val stateHolder = mutableStateOf(loaded)
    private var composed = false

    private fun ComposeUiTest.setScreen(state: MetronomeUiState, size: DpSize = foldContent) {
        stateHolder.value = state
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                TembraTheme {
                    val current by stateHolder
                    Box(modifier = Modifier.requiredSize(size)) {
                        MetronomeScreenContent(
                            state = current,
                            callbacks = callbacks,
                            telemetry = telemetry,
                            layout = MetronomeLayout.Tabletop,
                            snackbarHostState = SnackbarHostState(),
                        )
                    }
                }
            }
        }
        settle()
    }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.contains(other: DpRect, tolerance: Dp = 0.5.dp): Boolean =
        other.left >= left - tolerance && other.right <= right + tolerance && other.top >= top - tolerance && other.bottom <= bottom + tolerance

    private fun settingsFor(signature: TimeSignature) = MetronomeSettings(
        signature = signature,
        pattern = MetronomeStyle.Standard.pattern(signature),
        style = MetronomeStyle.Standard,
    )

    @Test
    fun onlyTheDialAndTheCellsAreAboveTheFold() = runComposeUiTest {
        setScreen(loaded)
        val upper = bounds("metronome.tabletop.upper")
        val lower = bounds("metronome.tabletop.lower")
        assertTrue(lower.top - upper.bottom >= 24.dp - 0.5.dp, "the regions $upper and $lower are closer than two gutters")
        for (tag in listOf("metronome.dialCard", "metronome.dial", "metronome.cells") + (1..4).map { "metronome.cell.$it" }) {
            assertTrue(upper.contains(bounds(tag)), "$tag (${bounds(tag)}) is not above the fold $upper")
        }
        // Design iteration D1: every control below the fold sits in one card, `metronome.settings`.
        val deck = bounds("metronome.settings")
        assertTrue(lower.contains(deck), "the controls card $deck is not below the fold $lower")
        for (tag in listOf("metronome.signature", "metronome.subdivision", "metronome.style", "metronome.sound", "metronome.slider", "metronome.tap", "metronome.play")) {
            assertTrue(deck.contains(bounds(tag)), "$tag (${bounds(tag)}) is not in the controls card $deck")
        }
        // The slider leaves the dial card in this form; the cells stay out of the controls card.
        onNodeWithTag("metronome.slider").assertNotIn("metronome.dialCard")
        onNodeWithTag("metronome.cells").assertNotIn("metronome.settings")
    }

    @Test
    fun theControlsCardIsHeadedAndReadsTopToBottom() = runComposeUiTest {
        setScreen(loaded)
        val heading = onNode(hasText("METRONOME CONTROLS") and hasAnyAncestor(hasTestTag("metronome.settings")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val deck = bounds("metronome.settings")
        assertTrue(abs(((heading.left + heading.right) / 2 - (deck.left + deck.right) / 2).value) < 1f, "the heading $heading is not centred in $deck")
        val order = listOf("metronome.signature", "metronome.style", "metronome.slider", "metronome.tap").map { bounds(it).top }
        assertTrue(heading.bottom <= order.first(), "the heading is not above the controls")
        assertEquals(order.sorted(), order, "signature, style, slider and tap are not in that order: $order")
        assertTrue(abs((bounds("metronome.sound").top - bounds("metronome.signature").top).value) < 8f, "the sound pill is not on the signature line")
    }

    @Test
    fun theDialKeepsAReadableSizeAndTheCellsGrowTall() = runComposeUiTest {
        setScreen(loaded)
        assertTrue(bounds("metronome.dial").height >= 160.dp - 0.5.dp, "the dial is under 160 dp")
        val bar = onNodeWithTag("metronome.cell.1.bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(bar.height > 64.dp, "the tabletop cells' bar box (${bar.height}) did not grow past the settings card's 64 dp")
    }

    @Test
    fun sevenEightKeepsEveryCellAboveTheFold() = runComposeUiTest {
        setScreen(loaded.copy(settings = settingsFor(TimeSignature.SevenEight)))
        val upper = bounds("metronome.tabletop.upper")
        for (n in 1..7) assertTrue(upper.contains(bounds("metronome.cell.$n")), "cell $n is not above the fold")
    }

    @Test
    fun theCellsStillCycleAndPlayStillToggles() = runComposeUiTest {
        setScreen(loaded)
        onNodeWithTag("metronome.cell.2").performClick()
        onNodeWithTag("metronome.play").performClick()
        settle()
        assertEquals(listOf(1), cycled)
        assertEquals(1, toggles)
    }

    /** A flip-style foldable half-opened: a 412 dp phone-width window, the fold across its middle. */
    @Test
    fun aNarrowTabletopStacksEachHalfAndKeepsEveryCellAboveTheFold() = runComposeUiTest {
        setScreen(loaded.copy(settings = settingsFor(TimeSignature.SevenEight)), size = DpSize(412.dp, 780.dp))
        val upper = bounds("metronome.tabletop.upper")
        val lower = bounds("metronome.tabletop.lower")
        val dialCard = bounds("metronome.dialCard")
        val cells = bounds("metronome.cells")
        assertTrue(upper.contains(dialCard) && upper.contains(cells), "the dial $dialCard or the cells $cells leave the upper region $upper")
        assertTrue(cells.top >= dialCard.bottom - 0.5.dp, "the cells are not under the dial when narrow")
        assertTrue(bounds("metronome.dial").height >= 160.dp - 0.5.dp, "the dial is under 160 dp")
        val tops = (1..7).map { bounds("metronome.cell.$it").top }.distinct()
        assertEquals(1, tops.size, "seven cells wrap above a narrow fold: $tops")
        for (n in 1..7) assertTrue(upper.contains(bounds("metronome.cell.$n")), "cell $n is not above the fold")
        for (tag in listOf("metronome.settings", "metronome.slider", "metronome.tap", "metronome.play")) {
            assertTrue(bounds(tag).top >= lower.top - 0.5.dp, "$tag is not below the fold")
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertNotIn(ancestorTag: String) {
        val node = fetchSemanticsNode()
        assertTrue(!hasAnyAncestor(hasTestTag(ancestorTag)).matches(node), "${node.config} is inside $ancestorTag")
    }
}
