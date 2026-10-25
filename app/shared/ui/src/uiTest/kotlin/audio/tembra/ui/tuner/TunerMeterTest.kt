package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.PitchDirection
import audio.tembra.engine.PitchSnapshot
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.isCloserTo
import audio.tembra.ui.designsystem.largeFonts
import audio.tembra.ui.designsystem.violetness
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The meter drawn from scripted telemetry: semantics, the in-tune pill, the needle side and the end labels (spec FR-008 to FR-010, FR-025). */
@OptIn(ExperimentalTestApi::class)
class TunerMeterTest {

    /** `primary` keeps blue well above red even at half opacity over the pill; `onSurfaceVariant` is a near grey. */
    private val LIT_VIOLETNESS = 0.06f

    private var primary = Color.Unspecified
    private var secondary = Color.Unspecified
    private var onSurfaceVariant = Color.Unspecified
    private var lowest = Color.Unspecified
    private var fontScale = 1f
    private var pixelDensity = 1f
    private val telemetry = ScriptedTelemetry()

    private fun snapshot(hz: Float?) = PitchSnapshot().apply {
        if (hz != null) {
            hasPitch = true
            frequencyHz = hz
        }
    }

    private fun reading(note: String, cents: Double, hz: Double): MeterReading {
        val n = Note.parse(note)
        val direction = when {
            kotlin.math.abs(cents) <= 3.0 -> PitchDirection.InTune
            cents < 0 -> PitchDirection.Flat
            else -> PitchDirection.Sharp
        }
        return MeterReading(n, cents, direction, direction == PitchDirection.InTune, hz, n.frequencyHz())
    }

    private val scaleHolder = mutableStateOf<Float?>(null)

    private fun ComposeUiTest.setMeter(reading: MeterReading?, hz: Float?, width: Dp = 340.dp, maxWidth: Dp = 340.dp, scale: Float? = null, listening: Boolean = true) {
        telemetry.snapshot.copyFrom(snapshot(hz))
        scaleHolder.value = scale
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                secondary = TembraTheme.colors.secondary
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                lowest = TembraTheme.colors.surfaceContainerLowest
                val fonts by scaleHolder
                val meter: @androidx.compose.runtime.Composable () -> Unit = {
                    fontScale = LocalDensity.current.fontScale
                    pixelDensity = LocalDensity.current.density
                    // The theme's lowest surface behind the meter: the pixel probes read it where nothing is drawn, whatever the
                    // window (black on Android) or the off-screen surface (iOS) would show.
                    Box(Modifier.width(width).background(TembraTheme.colors.surfaceContainerLowest)) {
                        TunerMeter(telemetry = telemetry, reading = reading, listening = listening, testTag = "tuner.meter", modifier = Modifier.widthIn(max = maxWidth))
                    }
                }
                val f = fonts
                if (f == null) meter() else CompositionLocalProvider(LocalDensity provides largeFonts(f)) { meter() }
            }
        }
        settle()
    }

    /**
     * Lets the needle settle: the smoothing has an 80 ms time constant and steps at most 100 ms per frame, so one long
     * jump of the clock leaves the needle short of its target; forty frames (640 ms) bring it within a hundredth of a degree.
     */
    private fun ComposeUiTest.settle() {
        repeat(40) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.meterImage(): Pair<ImageBitmap, MeterLayout> {
        val image = onNodeWithTag("tuner.meter").captureToImage()
        return image to MeterLayout(image.width.toFloat(), pixelDensity, fontScale)
    }

    private fun ImageBitmap.at(point: Pair<Float, Float>): Color = averageColorAt(point.first.roundToInt(), point.second.roundToInt())

    /** Red minus blue over the label's box: the pink highlight raises it, the grey resting label does not. One pixel map read. */
    private fun ImageBitmap.warmth(layout: MeterLayout, center: Pair<Float, Float>): Float {
        val pixels = toPixelMap()
        var sum = 0f
        var count = 0
        val x0 = (center.first - layout.labelProbeHalfWidth).roundToInt().coerceIn(0, pixels.width - 1)
        val x1 = (center.first + layout.labelProbeHalfWidth).roundToInt().coerceIn(0, pixels.width - 1)
        val y0 = (center.second - layout.labelProbeHalfHeight).roundToInt().coerceIn(0, pixels.height - 1)
        val y1 = (center.second + layout.labelProbeHalfHeight).roundToInt().coerceIn(0, pixels.height - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val c = pixels[x, y]
            sum += c.red - c.blue
            count++
        }
        return sum / count
    }

    @Test
    fun inTuneLightsThePillAndCentresTheNeedle() = runComposeUiTest {
        setMeter(reading("E2", 2.0, 82.5), hz = 82.5f)
        onNodeWithTag("tuner.meter")
            .assertContentDescriptionEquals("Tuner meter")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "E2, in tune"))
        val (image, layout) = meterImage()
        val dot = image.at(layout.pillDotCenter)
        assertTrue(dot.violetness() > LIT_VIOLETNESS, "pill dot $dot is not lit in primary")
        val needle = image.at(layout.toMeter(layout.geometry.needlePoint(2.0, 0.85f)))
        assertTrue(needle.isCloserTo(secondary, lowest), "needle block $needle is not secondary")
        val flat = image.warmth(layout, layout.flatLabelCenter)
        val sharp = image.warmth(layout, layout.sharpLabelCenter)
        assertTrue(kotlin.math.abs(flat - sharp) < 0.02f, "labels differ while in tune: flat $flat sharp $sharp")
    }

    @Test
    fun flatHighlightsTheFlatLabel() = runComposeUiTest {
        setMeter(reading("E2", -20.0, 81.46), hz = 81.46f)
        onNodeWithTag("tuner.meter").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "E2, 20 cents flat"))
        val (image, layout) = meterImage()
        assertTrue(image.warmth(layout, layout.flatLabelCenter) > image.warmth(layout, layout.sharpLabelCenter) + 0.01f, "Flat is not highlighted")
        val needle = image.at(layout.toMeter(layout.geometry.needlePoint(-20.0, 0.85f)))
        assertTrue(needle.isCloserTo(secondary, lowest), "needle block $needle is not on the flat side")
        val dot = image.at(layout.pillDotCenter)
        assertTrue(dot.violetness() < LIT_VIOLETNESS, "pill dot $dot should be unlit")
    }

    @Test
    fun sharpHighlightsTheSharpLabel() = runComposeUiTest {
        setMeter(reading("E2", 20.0, 83.36), hz = 83.36f)
        onNodeWithTag("tuner.meter").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "E2, 20 cents sharp"))
        val (image, layout) = meterImage()
        assertTrue(image.warmth(layout, layout.sharpLabelCenter) > image.warmth(layout, layout.flatLabelCenter) + 0.01f, "Sharp is not highlighted")
        val needle = image.at(layout.toMeter(layout.geometry.needlePoint(20.0, 0.85f)))
        assertTrue(needle.isCloserTo(secondary, lowest), "needle block $needle is not on the sharp side")
    }

    /** Beyond ±50 cents the nearest note changes, so a clamped reading only reaches the meter through [reading] when not listening. */
    @Test
    fun theNeedleClampsAtTheEndOfTheScale() = runComposeUiTest {
        setMeter(reading("E2", 80.0, 86.3), hz = null, listening = false)
        val (image, layout) = meterImage()
        val needle = image.at(layout.toMeter(layout.geometry.needlePoint(50.0, 0.85f)))
        assertTrue(needle.isCloserTo(secondary, lowest), "needle block $needle is not at the clamped end")
    }

    @Test
    fun restingShowsListeningWithNothingLit() = runComposeUiTest {
        setMeter(reading = null, hz = null)
        onNodeWithTag("tuner.meter").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Listening"))
        val (image, layout) = meterImage()
        val dot = image.at(layout.pillDotCenter)
        assertTrue(dot.violetness() < LIT_VIOLETNESS, "pill dot $dot should be unlit")
        val flat = image.warmth(layout, layout.flatLabelCenter)
        val sharp = image.warmth(layout, layout.sharpLabelCenter)
        assertTrue(kotlin.math.abs(flat - sharp) < 0.02f, "a label is highlighted while resting: flat $flat sharp $sharp")
    }

    @Test
    fun theMeterKeepsItsAspectAndCapsItsWidth() = runComposeUiTest {
        setMeter(reading("E2", 0.0, 82.41), hz = 82.41f, width = 400.dp, maxWidth = 340.dp)
        val bounds = onNodeWithTag("tuner.meter").getUnclippedBoundsInRoot()
        assertTrue(bounds.width <= 340.dp + 0.5.dp, "width ${bounds.width} exceeds the cap")
        val (image, layout) = meterImage()
        assertEquals(layout.height.roundToInt(), image.height)
        assertTrue(kotlin.math.abs(layout.arcHeight - image.width / 1.8f) < 1f)
    }

    @Test
    fun largeFontsGrowTheTextRows() = runComposeUiTest {
        setMeter(reading("E2", 0.0, 82.41), hz = 82.41f)
        val normal = onNodeWithTag("tuner.meter").getUnclippedBoundsInRoot().height
        scaleHolder.value = 2f
        settle()
        val large = onNodeWithTag("tuner.meter").getUnclippedBoundsInRoot().height
        assertTrue(large > normal, "large fonts did not grow the meter: $normal -> $large")
    }
}
