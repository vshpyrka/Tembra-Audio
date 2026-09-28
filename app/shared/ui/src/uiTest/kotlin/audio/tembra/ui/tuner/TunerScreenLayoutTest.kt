package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.engine.AudioState
import audio.tembra.engine.PitchSnapshot
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.designsystem.largeFonts
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Layouts, overlap, keyboard order, targets, semantics and reduce motion (spec FR-023 to FR-027; User Story 6). */
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
    private val byEarCello = byEarSeven.copy(
        instrument = Instrument.Cello,
        preset = TuningPresets.byId("cello-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Cello, null),
    )

    /** A Pixel 6 Pro held sideways, less the status and top bars (quickstart §8). */
    private val phoneLandscape = DpSize(892.dp, 330.dp)

    private val stateHolder = mutableStateOf(TunerUiState())
    private val layoutHolder = mutableStateOf(TunerLayout.Stacked)
    private val scaleHolder = mutableStateOf<Float?>(null)
    private val sizeHolder = mutableStateOf<DpSize?>(null)
    private var composed = false

    /** Composes once per test and re-configures through state; the frame loop is stepped by hand. */
    private fun ComposeUiTest.setScreen(state: TunerUiState, layout: TunerLayout, scale: Float? = null, size: DpSize? = null) {
        stateHolder.value = state
        layoutHolder.value = layout
        scaleHolder.value = scale
        sizeHolder.value = size
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                inputModeManager = LocalInputModeManager.current
                TembraTheme {
                    val current by stateHolder
                    val currentLayout by layoutHolder
                    val fonts by scaleHolder
                    val window by sizeHolder
                    val content: @androidx.compose.runtime.Composable () -> Unit = {
                        fontScale = LocalDensity.current.fontScale
                        pixelDensity = LocalDensity.current.density
                        val sized = window
                        Box(modifier = if (sized != null) Modifier.requiredSize(sized) else Modifier) {
                            TunerScreenContent(state = current, callbacks = TunerCallbacks.None, telemetry = telemetry, layout = currentLayout)
                        }
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
    fun oneSelectorRowWhenWide() = runComposeUiTest {
        setScreen(autoListen, TunerLayout.Wide)
        val tops = listOf("tuner.instrument", "tuner.pill", "tuner.strings", "tuner.mode").map { bounds(it) }
        for (b in tops) assertTrue(abs((b.top + b.bottom).value / 2 - (tops[0].top + tops[0].bottom).value / 2) < 1f, "selectors are not on one row: $tops")
    }

    @Test
    fun twoRowsWhenStacked() = runComposeUiTest {
        setScreen(autoListen, TunerLayout.Stacked)
        assertTrue(bounds("tuner.strings").top >= bounds("tuner.instrument").bottom, "strings are not below the instrument selector")
    }

    @Test
    fun meterCappedAt480AndCentred() = runComposeUiTest {
        setScreen(autoListen, TunerLayout.Wide)
        val meter = bounds("tuner.meter")
        val card = bounds("tuner.autoListen")
        assertTrue(meter.width <= 480.dp + 0.5.dp, "meter width ${meter.width}")
        val meterCentre = (meter.left + meter.right) / 2
        val cardCentre = (card.left + card.right) / 2
        assertTrue(abs((meterCentre - cardCentre).value) < 1f, "meter is not centred: $meterCentre vs $cardCentre")
    }

    @Test
    fun threeColumnsWhenWideTwoWhenStacked() = runComposeUiTest {
        setScreen(byEarSeven, TunerLayout.Wide)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.VI").top)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.V").top)
        assertTrue(bounds("tuner.card.IV").top > bounds("tuner.card.VII").bottom, "IV does not start the second row")
        setScreen(byEarSeven, TunerLayout.Stacked)
        assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.VI").top)
        assertTrue(bounds("tuner.card.V").top > bounds("tuner.card.VII").bottom, "stacked does not use two columns")
    }

    @Test
    fun landscapeStacksTheSelectorsBesideTheContent() = runComposeUiTest {
        setScreen(autoListen, TunerLayout.Landscape, size = phoneLandscape)
        val settings = bounds("tuner.settings")
        val card = bounds("tuner.autoListen")
        assertTrue(settings.right <= card.left, "the settings column $settings is not left of the card $card")
        assertTrue(settings.width >= 224.dp - 0.5.dp && settings.width <= 280.dp + 0.5.dp, "settings width ${settings.width}")
        assertTrue(bounds("tuner.pill").top < bounds("tuner.instrument").top, "the pill is not in the column header")
        assertTrue(bounds("tuner.strings").top >= bounds("tuner.instrument").bottom, "strings are not below the instrument selector")
        assertTrue(bounds("tuner.mode").top >= bounds("tuner.strings").bottom, "mode is not below the string count")
        for (tag in listOf("tuner.instrument", "tuner.strings", "tuner.mode")) {
            assertTrue(bounds(tag).right <= settings.right + 0.5.dp && bounds(tag).left >= settings.left - 0.5.dp, "$tag leaves the column")
        }
    }

    @Test
    fun landscapeMeterFitsWithoutScrolling() = runComposeUiTest {
        setScreen(autoListen, TunerLayout.Landscape, size = phoneLandscape)
        val meter = bounds("tuner.meter")
        val card = bounds("tuner.autoListen")
        assertTrue(meter.top >= card.top && meter.bottom <= card.bottom, "the meter $meter overflows the card $card")
        assertTrue(meter.width >= 200.dp - 0.5.dp, "meter width ${meter.width}")
        val meterCentre = (meter.top + meter.bottom) / 2
        val cardCentre = (card.top + card.bottom) / 2
        assertTrue(abs((meterCentre - cardCentre).value) < 1f, "meter is not vertically centred: $meterCentre vs $cardCentre")
    }

    @Test
    fun landscapeCardsFillTwoRows() = runComposeUiTest {
        setScreen(byEarSeven, TunerLayout.Landscape, size = phoneLandscape)
        val card = bounds("tuner.byEar")
        for (numeral in listOf("VI", "V", "IV")) assertEquals(bounds("tuner.card.VII").top, bounds("tuner.card.$numeral").top, "$numeral is not on the first row")
        assertTrue(bounds("tuner.card.III").top > bounds("tuner.card.VII").bottom, "III does not start the second row")
        assertTrue(bounds("tuner.card.I").bottom <= card.bottom + 0.5.dp, "the last card ${bounds("tuner.card.I")} leaves the card $card")
        assertTrue(bounds("tuner.card.VII").height >= 128.dp - 0.5.dp, "cards are shorter than the landscape minimum")
        assertTrue(bounds("tuner.preset.picker").right <= card.left, "the picker is not in the settings column")
        assertTrue(bounds("tuner.loop").right <= card.left, "the toggle is not in the settings column")
        setScreen(byEarCello, TunerLayout.Landscape, size = phoneLandscape)
        assertEquals(bounds("tuner.card.IV").top, bounds("tuner.card.III").top)
        assertTrue(bounds("tuner.card.II").top > bounds("tuner.card.IV").bottom, "the cello does not use two columns")
    }

    @Test
    fun nothingOverlaps() = runComposeUiTest {
        for ((layout, size) in listOf(TunerLayout.Stacked to null, TunerLayout.Wide to null, TunerLayout.Landscape to phoneLandscape)) {
            for (scale in listOf(null, 2f)) {
                setScreen(autoListen, layout, scale, size)
                val nodes = listOf("tuner.instrument", "tuner.pill", "tuner.strings", "tuner.mode", "tuner.meter").map { it to bounds(it) }
                for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                    assertTrue(!nodes[i].second.intersects(nodes[j].second), "$layout/$scale: ${nodes[i].first} overlaps ${nodes[j].first}")
                }
                setScreen(byEarSeven, layout, scale, size)
                val cards = byEarSeven.strings.map { "tuner.card.${it.numeral}" to bounds("tuner.card.${it.numeral}") } +
                    listOf("tuner.preset.picker" to bounds("tuner.preset.picker"), "tuner.loop" to bounds("tuner.loop"), "tuner.mode" to bounds("tuner.mode"))
                for (i in cards.indices) for (j in i + 1 until cards.size) {
                    assertTrue(!cards[i].second.intersects(cards[j].second), "$layout/$scale: ${cards[i].first} overlaps ${cards[j].first}")
                }
            }
        }
    }

    @Test
    fun keyboardOrderFollowsReading() = runComposeUiTest {
        setScreen(byEarSeven, TunerLayout.Stacked)
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        val expected = listOf(
            "tuner.instrument.cello", "tuner.strings.6", "tuner.strings.7", "tuner.mode.autoListen", "tuner.mode.byEar",
            "tuner.preset.picker", "tuner.loop", "tuner.card.VII.audition", "tuner.card.VI.audition",
        )
        for ((layout, size) in listOf(TunerLayout.Stacked to null, TunerLayout.Landscape to phoneLandscape)) {
            setScreen(byEarSeven, layout, size = size)
            onNodeWithTag("tuner.instrument.guitar").requestFocus()
            onNodeWithTag("tuner.instrument.guitar").assertIsFocused()
            for (tag in expected) {
                onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
                repeat(2) { mainClock.advanceTimeByFrame() }
                onNodeWithTag(tag).assertIsFocused()
            }
        }
    }

    @Test
    fun allTargetsAtLeast44dp() = runComposeUiTest {
        for ((layout, size) in listOf(TunerLayout.Stacked to null, TunerLayout.Landscape to phoneLandscape)) {
            setScreen(byEarSeven, layout, size = size)
            val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
            for (node in onAllNodes(clickable).fetchSemanticsNodes()) {
                val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: continue
                onNodeWithTag(tag).assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
            }
        }
    }

    @Test
    fun semantics() = runComposeUiTest {
        setScreen(byEarSeven, TunerLayout.Stacked)
        for (string in byEarSeven.strings) {
            onNodeWithTag("tuner.card.${string.numeral}.audition").assertContentDescriptionEquals("Audition ${string.note.name}")
        }
        onNodeWithTag("tuner.instrument.guitar").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithTag("tuner.instrument.guitar").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("tuner.loop").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        setScreen(autoListen, TunerLayout.Stacked)
        onNodeWithTag("tuner.meter").assertContentDescriptionEquals("Tuner meter")
    }

    @Test
    fun reduceMotionStillMovesNeedle() = runComposeUiTest(effectContext = zeroScale) {
        telemetry.snapshot.copyFrom(PitchSnapshot().apply { hasPitch = true; frequencyHz = 83.36f })
        setScreen(autoListen, TunerLayout.Stacked)
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
        assertTrue(abs(dotBefore.alpha - dotAfter.alpha) < 0.01f, "pill dot pulses under reduce motion")
    }
}
