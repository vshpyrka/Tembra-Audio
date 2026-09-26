package audio.tembra.ui.tuner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.engine.AudioState
import audio.tembra.engine.PitchSnapshot
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.largeFonts
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Window widths, overlap, keyboard order, targets, semantics and reduce motion (spec FR-023 to FR-027; User Story 6). */
@OptIn(ExperimentalTestApi::class)
class TunerScreenLayoutTest {

    private val telemetry = ScriptedTelemetry()
    private var inputModeManager: InputModeManager? = null
    private var fontScale = 1f
    private var pixelDensity = 1f

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private val autoListen = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, audio = AudioState.Listening)
    private val byEarSeven = TunerUiState(
        loaded = true,
        microphone = MicrophonePermissionStatus.Granted,
        mode = TunerMode.ByEar,
        guitarStrings = GuitarStringCount.Seven,
        preset = TuningPresets.byId("guitar7-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Guitar, GuitarStringCount.Seven),
    )

    private val stateHolder = mutableStateOf(TunerUiState())
    private val widthHolder = mutableStateOf(WindowWidth.Compact)
    private val scaleHolder = mutableStateOf<Float?>(null)
    private var composed = false

    /** Composes once per test and re-configures through state; the frame loop is stepped by hand. */
    private fun ComposeUiTest.setScreen(state: TunerUiState, windowWidth: WindowWidth, scale: Float? = null) {
        stateHolder.value = state
        widthHolder.value = windowWidth
        scaleHolder.value = scale
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                inputModeManager = LocalInputModeManager.current
                TembraTheme {
                    val current by stateHolder
                    val width by widthHolder
                    val fonts by scaleHolder
                    val content: @androidx.compose.runtime.Composable () -> Unit = {
                        fontScale = LocalDensity.current.fontScale
                        pixelDensity = LocalDensity.current.density
                        TunerScreenContent(state = current, callbacks = TunerCallbacks.None, telemetry = telemetry, windowWidth = width)
                    }
                    val f = fonts
                    if (f == null) content() else CompositionLocalProvider(LocalDensity provides largeFonts(f)) { content() }
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.intersects(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun oneSelectorRowAtExpanded() = runComposeUiTest {
        setScreen(autoListen, WindowWidth.Expanded)
        val tops = listOf("tuner.instrument", "tuner.pill", "tuner.strings", "tuner.mode").map { bounds(it) }
        for (b in tops) assertTrue(kotlin.math.abs((b.top + b.bottom).value / 2 - (tops[0].top + tops[0].bottom).value / 2) < 1f, "selectors are not on one row: $tops")
    }

    @Test
    fun twoRowsAtCompactAndMedium() = runComposeUiTest {
        for (width in listOf(WindowWidth.Compact, WindowWidth.Medium)) {
            setScreen(autoListen, width)
            assertTrue(bounds("tuner.strings").top >= bounds("tuner.instrument").bottom, "$width: strings are not below the instrument selector")
        }
    }

    @Test
    fun meterCappedAt480AndCentred() = runComposeUiTest {
        setScreen(autoListen, WindowWidth.Expanded)
        val meter = bounds("tuner.meter")
        val card = bounds("tuner.autoListen")
        assertTrue(meter.width <= 480.dp + 0.5.dp, "meter width ${meter.width}")
        val meterCentre = (meter.left + meter.right) / 2
        val cardCentre = (card.left + card.right) / 2
        assertTrue(kotlin.math.abs((meterCentre - cardCentre).value) < 1f, "meter is not centred: $meterCentre vs $cardCentre")
    }

    @Test
    fun threeColumnsAtExpandedTwoAtMedium() = runComposeUiTest {
        setScreen(byEarSeven, WindowWidth.Expanded)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.VI").top)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.V").top)
        assertTrue(bounds("tuner.card.IV").top > bounds("tuner.card.VII").bottom, "IV does not start the second row")
        setScreen(byEarSeven, WindowWidth.Medium)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.VI").top)
        assertTrue(bounds("tuner.card.V").top > bounds("tuner.card.VII").bottom, "medium does not use two columns")
    }

    @Test
    fun nothingOverlaps() = runComposeUiTest {
        for (width in listOf(WindowWidth.Compact, WindowWidth.Expanded)) {
            for (scale in listOf(null, 2f)) {
                setScreen(autoListen, width, scale)
                val nodes = listOf("tuner.instrument", "tuner.pill", "tuner.strings", "tuner.mode", "tuner.meter").map { it to bounds(it) }
                for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                    assertTrue(!nodes[i].second.intersects(nodes[j].second), "$width/$scale: ${nodes[i].first} overlaps ${nodes[j].first}")
                }
                setScreen(byEarSeven, width, scale)
                val cards = byEarSeven.strings.map { "tuner.card.${it.numeral}" to bounds("tuner.card.${it.numeral}") } +
                    listOf("tuner.preset.picker" to bounds("tuner.preset.picker"), "tuner.loop" to bounds("tuner.loop"), "tuner.mode" to bounds("tuner.mode"))
                for (i in cards.indices) for (j in i + 1 until cards.size) {
                    assertTrue(!cards[i].second.intersects(cards[j].second), "$width/$scale: ${cards[i].first} overlaps ${cards[j].first}")
                }
            }
        }
    }

    @Test
    fun keyboardOrderFollowsReading() = runComposeUiTest {
        setScreen(byEarSeven, WindowWidth.Compact)
        val keyboard = runOnUiThread { inputModeManager!!.requestInputMode(InputMode.Keyboard) }
        if (!keyboard) {
            println("keyboardOrderFollowsReading: platform stays in touch mode, keyboard path covered on iOS")
            return@runComposeUiTest
        }
        onNodeWithTag("tuner.instrument.guitar").requestFocus()
        onNodeWithTag("tuner.instrument.guitar").assertIsFocused()
        val expected = listOf(
            "tuner.instrument.cello", "tuner.strings.6", "tuner.strings.7", "tuner.mode.autoListen", "tuner.mode.byEar",
            "tuner.preset.picker", "tuner.loop", "tuner.card.VII.audition", "tuner.card.VI.audition",
        )
        for (tag in expected) {
            onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            repeat(2) { mainClock.advanceTimeByFrame() }
            onNodeWithTag(tag).assertIsFocused()
        }
    }

    @Test
    fun allTargetsAtLeast44dp() = runComposeUiTest {
        setScreen(byEarSeven, WindowWidth.Compact)
        val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        for (node in onAllNodes(clickable).fetchSemanticsNodes()) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: continue
            onNodeWithTag(tag).assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun semantics() = runComposeUiTest {
        setScreen(byEarSeven, WindowWidth.Compact)
        for (string in byEarSeven.strings) {
            onNodeWithTag("tuner.card.${string.numeral}.audition").assertContentDescriptionEquals("Audition ${string.note.name}")
        }
        onNodeWithTag("tuner.instrument.guitar").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithTag("tuner.instrument.guitar").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("tuner.loop").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        setScreen(autoListen, WindowWidth.Compact)
        onNodeWithTag("tuner.meter").assertContentDescriptionEquals("Tuner meter")
    }

    @Test
    fun reduceMotionStillMovesNeedle() = runComposeUiTest(effectContext = zeroScale) {
        telemetry.snapshot.copyFrom(PitchSnapshot().apply { hasPitch = true; frequencyHz = 83.36f })
        setScreen(autoListen, WindowWidth.Compact)
        repeat(3) { mainClock.advanceTimeByFrame() }
        val sharp = onNodeWithTag("tuner.meter").captureToImage()
        val layout = MeterLayout(sharp.width.toFloat(), pixelDensity, fontScale)
        val probe = layout.toMeter(layout.geometry.needlePoint(20.0, 0.85f))
        val sharpBlock = sharp.averageColorAt(probe.first.roundToInt(), probe.second.roundToInt())
        val dotBefore = sharp.averageColorAt(layout.pillDotCenter.first.roundToInt(), layout.pillDotCenter.second.roundToInt())
        telemetry.snapshot.frequencyHz = 81.46f
        repeat(3) { mainClock.advanceTimeByFrame() }
        val flat = onNodeWithTag("tuner.meter").captureToImage()
        val flatBlock = flat.averageColorAt(probe.first.roundToInt(), probe.second.roundToInt())
        val dotAfter = flat.averageColorAt(layout.pillDotCenter.first.roundToInt(), layout.pillDotCenter.second.roundToInt())
        assertTrue(sharpBlock.red - flatBlock.red > 0.1f || sharpBlock.green - flatBlock.green > 0.1f, "needle did not move under reduce motion: $sharpBlock -> $flatBlock")
        assertTrue(kotlin.math.abs(dotBefore.alpha - dotAfter.alpha) < 0.01f, "pill dot pulses under reduce motion")
    }
}
