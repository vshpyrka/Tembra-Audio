package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.engine.AudioState
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tuner above and below a tabletop fold (feature 005, spec FR-003, FR-004, FR-010; contracts/tabletop-layout.md).
 * The content composable is driven with `layout = Tabletop` and no fold, so the regions split the host in half.
 */
@OptIn(ExperimentalTestApi::class)
class TunerTabletopLayoutTest {

    private val telemetry = ScriptedTelemetry()
    private val auditions = mutableListOf<Int>()
    private val callbacks = TunerCallbacks.None.copy(onAudition = { auditions += it })

    /** The Pixel 9 Pro Fold's inner screen half-opened, less the status bar, the shell's former top bar and the rail. */
    private val foldContent = DpSize(800.dp, 720.dp)

    private val autoListen = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, audio = AudioState.Listening)
    private val byEarSeven = autoListen.copy(
        mode = TunerMode.ByEar,
        guitarStrings = GuitarStringCount.Seven,
        preset = TuningPresets.byId("guitar7-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Seven),
    )
    private val byEarCello = byEarSeven.copy(
        instrument = Instrument.Cello,
        preset = TuningPresets.byId("cello-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Cello, null),
    )

    private val stateHolder = mutableStateOf(TunerUiState())
    private var composed = false

    /** Composes once per test (Android allows one `setContent`) and re-configures through state. */
    private fun ComposeUiTest.setScreen(state: TunerUiState) {
        stateHolder.value = state
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                TembraTheme {
                    val current by stateHolder
                    Box(modifier = Modifier.requiredSize(foldContent)) {
                        TunerScreenContent(state = current, callbacks = callbacks, telemetry = telemetry, layout = TunerLayout.Tabletop)
                    }
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.contains(other: DpRect, tolerance: Dp = 0.5.dp): Boolean =
        other.left >= left - tolerance && other.right <= right + tolerance && other.top >= top - tolerance && other.bottom <= bottom + tolerance

    private fun ComposeUiTest.assertRegions(upperTags: List<String>, lowerTags: List<String>) {
        val upper = bounds("tuner.tabletop.upper")
        val lower = bounds("tuner.tabletop.lower")
        // Two gutters of spacing.md (12 dp) keep the regions clear of the fold.
        assertTrue(lower.top - upper.bottom >= 24.dp - 0.5.dp, "the regions $upper and $lower are closer than two gutters")
        for (tag in upperTags) assertTrue(upper.contains(bounds(tag)), "$tag (${bounds(tag)}) is not above the fold $upper")
        for (tag in lowerTags) assertTrue(lower.contains(bounds(tag)), "$tag (${bounds(tag)}) is not below the fold $lower")
    }

    @Test
    fun autoListenShowsTheMeterAboveAndTheSelectorsBelow() = runComposeUiTest {
        setScreen(autoListen)
        assertRegions(
            upperTags = listOf("tuner.autoListen", "tuner.meter"),
            lowerTags = listOf("tuner.settings", "tuner.instrument", "tuner.pill", "tuner.mode"),
        )
        onNodeWithTag("tuner.preset.picker").assertDoesNotExist()
    }

    /** Feature 006: the noise gate is a setting, so it goes below the fold under the mode control; the gauge keeps the upper region. */
    @Test
    fun theNoiseGateSitsUnderTheModeControlBelowTheFold() = runComposeUiTest {
        setScreen(autoListen)
        assertRegions(upperTags = listOf("tuner.autoListen", "tuner.meter"), lowerTags = listOf("tuner.settings", "tuner.mode", "tuner.noiseGate"))
        assertEquals(1, onAllNodesWithTag("tuner.noiseGate").fetchSemanticsNodes().size)
        val gate = bounds("tuner.noiseGate")
        assertTrue(bounds("tuner.settings").contains(gate), "the noise gate $gate is outside the settings card ${bounds("tuner.settings")}")
        assertTrue(gate.top >= bounds("tuner.mode").bottom - 0.5.dp, "the noise gate $gate is not under the mode control ${bounds("tuner.mode")}")
        setScreen(byEarSeven)
        onNodeWithTag("tuner.noiseGate").assertDoesNotExist()
    }

    @Test
    fun aRefusedMicrophoneShowsItsActionAboveTheFold() = runComposeUiTest {
        setScreen(autoListen.copy(microphone = MicrophonePermissionStatus.DeniedOpenSettings, audio = AudioState.Idle))
        assertRegions(upperTags = listOf("tuner.denied", "tuner.denied.action"), lowerTags = listOf("tuner.instrument", "tuner.mode"))
    }

    @Test
    fun byEarShowsEverySevenStringCardAboveAndItsSettingsBelow() = runComposeUiTest {
        setScreen(byEarSeven)
        assertRegions(
            upperTags = listOf("tuner.byEar") + listOf("I", "II", "III", "IV", "V", "VI", "VII").map { "tuner.card.$it" },
            lowerTags = listOf("tuner.instrument", "tuner.mode", "tuner.strings", "tuner.preset.picker", "tuner.loop"),
        )
    }

    @Test
    fun theCelloHasNoStringCount() = runComposeUiTest {
        setScreen(byEarCello)
        assertRegions(
            upperTags = listOf("I", "II", "III", "IV").map { "tuner.card.$it" },
            lowerTags = listOf("tuner.preset.picker", "tuner.loop"),
        )
        onNodeWithTag("tuner.strings").assertDoesNotExist()
    }

    /** Design iteration D1: the controls stack in one centred column under a centred heading. */
    @Test
    fun theControlsStackUnderACentredHeading() = runComposeUiTest {
        setScreen(byEarSeven)
        val card = bounds("tuner.settings")
        val heading = onNode(hasText("TUNER CONTROLS") and hasAnyAncestor(hasTestTag("tuner.settings")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(abs(((heading.left + heading.right) / 2 - (card.left + card.right) / 2).value) < 1f, "the heading $heading is not centred in $card")
        val stack = listOf("tuner.instrument", "tuner.mode", "tuner.strings", "tuner.preset.picker", "tuner.loop").map { bounds(it) }
        for ((above, below) in stack.zipWithNext()) assertTrue(below.top >= above.bottom - 0.5.dp, "$below is not under $above")
        val centre = (card.left + card.right) / 2
        // `tuner.loop` is the switch at the end of its full-width row, so only the first four are checked for centring.
        for (b in stack.dropLast(1)) assertTrue(abs(((b.left + b.right) / 2 - centre).value) < 1f, "$b is not centred in the card")
    }

    @Test
    fun aCardAboveTheFoldStillAuditions() = runComposeUiTest {
        setScreen(byEarSeven)
        onNodeWithTag("tuner.card.I.audition").performClick()
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertEquals(listOf(byEarSeven.strings.indexOfFirst { it.numeral == "I" }), auditions)
    }
}
