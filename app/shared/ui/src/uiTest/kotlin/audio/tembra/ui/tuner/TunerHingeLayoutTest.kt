package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.engine.AudioState
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wide tuner on a foldable half-opened like a book (design iteration D3): the "Tuner controls" card ends a gutter
 * before the hinge and the gauge or the string cards start a gutter after it. The host sits at the window's origin, so
 * a fold 426 dp into the window is 426 dp into the root.
 */
@OptIn(ExperimentalTestApi::class)
class TunerHingeLayoutTest {

    private val autoListen = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, audio = AudioState.Listening)
    private val byEarSeven = autoListen.copy(
        mode = TunerMode.ByEar,
        guitarStrings = GuitarStringCount.Seven,
        preset = TuningPresets.byId("guitar7-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Seven),
    )
    private val stateHolder = mutableStateOf(autoListen)
    private var composed = false

    private fun ComposeUiTest.setScreen(state: TunerUiState) {
        stateHolder.value = state
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                TembraTheme {
                    val current by stateHolder
                    val px = LocalDensity.current.density * 426f
                    Box(modifier = Modifier.requiredSize(DpSize(852.dp, 760.dp)).onPlaced { hostX.floatValue = it.positionInWindow().x }.testTag(HOST)) {
                        TunerScreenContent(
                            state = current,
                            callbacks = TunerCallbacks.None,
                            telemetry = ScriptedTelemetry(),
                            layout = TunerLayout.Wide,
                            verticalFold = VerticalFold(left = hostX.floatValue + px, right = hostX.floatValue + px),
                        )
                    }
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    /** The host's left edge in window pixels, read back so the fold sits 426 dp into the host. */
    private val hostX = mutableFloatStateOf(0f)

    /** [tag]'s bounds relative to the host. */
    private fun ComposeUiTest.bounds(tag: String): DpRect {
        val host = onNodeWithTag(HOST).getUnclippedBoundsInRoot()
        val node = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return DpRect(node.left - host.left, node.top - host.top, node.right - host.left, node.bottom - host.top)
    }

    @Test
    fun autoListenPutsTheControlsBeforeTheHingeAndTheGaugeAfterIt() = runComposeUiTest {
        setScreen(autoListen)
        val settings = bounds("tuner.settings")
        assertTrue(settings.right <= 414.dp + 1.dp, "the controls $settings reach the hinge's gutter at 414 dp")
        for (tag in listOf("tuner.instrument", "tuner.mode", "tuner.pill")) {
            assertTrue(bounds(tag).right <= 414.dp + 1.dp, "$tag is not before the hinge")
        }
        assertTrue(bounds("tuner.autoListen").left >= 438.dp - 1.dp, "the gauge card starts inside the hinge's gutter")
        assertTrue(bounds("tuner.meter").left >= 438.dp - 1.dp, "the meter starts inside the hinge's gutter")
    }

    /** In the narrow controls card the heading never overlaps the audio-state pill: the pill drops under it. */
    @Test
    fun theHeadingAndThePillDoNotOverlapInTheNarrowCard() = runComposeUiTest {
        setScreen(autoListen.copy(audio = AudioState.Listening))
        val heading = onNode(hasText("TUNER CONTROLS") and hasAnyAncestor(hasTestTag("tuner.settings")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // Raw root bounds here, like the heading's: only their relation matters.
        val pill = onNodeWithTag("tuner.pill").getUnclippedBoundsInRoot()
        val disjoint = heading.right <= pill.left || pill.right <= heading.left || heading.bottom <= pill.top || pill.bottom <= heading.top
        assertTrue(disjoint, "the heading $heading overlaps the pill $pill")
        val card = onNodeWithTag("tuner.settings").getUnclippedBoundsInRoot()
        assertTrue(abs(((heading.left + heading.right) / 2 - (card.left + card.right) / 2).value) < 1f, "the heading is not centred in $card")
    }

    @Test
    fun byEarShowsEverySquareCardAfterTheHingeAndItsControlsOnceBeforeIt() = runComposeUiTest {
        setScreen(byEarSeven)
        val pane = bounds("tuner.byEar")
        assertTrue(pane.left >= 438.dp - 1.dp, "the By Ear card $pane starts inside the hinge's gutter")
        for (numeral in listOf("VII", "VI", "V", "IV", "III", "II", "I")) {
            val card = bounds("tuner.card.$numeral")
            assertTrue(card.left >= pane.left && card.right <= pane.right && card.top >= pane.top && card.bottom <= pane.bottom, "card $numeral $card is outside $pane")
            assertTrue(abs((card.width - card.height).value) < 1f, "card $numeral is not square")
        }
        // The preset, string count and loop tone live only in the controls card, before the hinge.
        assertEquals(1, onAllNodesWithTag("tuner.preset.picker").fetchSemanticsNodes().size)
        assertTrue(bounds("tuner.preset.picker").right <= 414.dp + 1.dp, "the preset picker is not before the hinge")
        assertTrue(bounds("tuner.strings").right <= 414.dp + 1.dp, "the string count is not before the hinge")
    }
}

private const val HOST = "test.host"
