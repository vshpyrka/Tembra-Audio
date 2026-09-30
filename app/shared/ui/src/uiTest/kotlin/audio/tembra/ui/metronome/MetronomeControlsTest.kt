package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.designsystem.luminance
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.metronome.components.TapTempoButton
import audio.tembra.ui.metronome.components.TempoSlider
import kotlin.math.roundToInt
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Hover and focus feedback of the tap card and the slider thumb, and the play button's filled glyphs (Constitution IV). */
@OptIn(ExperimentalTestApi::class)
class MetronomeControlsTest {

    private var primary = Color.Unspecified
    private var pixelDensity = 1f

    private fun ComposeUiTest.emit(source: MutableInteractionSource, interaction: androidx.compose.foundation.interaction.Interaction) {
        runOnIdle { source.tryEmit(interaction) }
        repeat(2) { mainClock.advanceTimeByFrame() }
    }

    /** Pixels that changed between two captures of the same node, and whether they moved towards `primary`. */
    private fun changedTowardsPrimary(before: ImageBitmap, after: ImageBitmap): Int {
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (a[x, y].distanceTo(b[x, y]) > 0.05f && b[x, y].distanceTo(primary) < a[x, y].distanceTo(primary)) count++
        }
        return count
    }

    @Test
    fun hoverRaisesTheTapCard() = runComposeUiTest {
        val source = MutableInteractionSource()
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                pixelDensity = LocalDensity.current.density
                Box(Modifier.width(240.dp)) {
                    TapTempoButton(caption = "Sync finger rhythm", onTap = {}, interactionSource = source)
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
        fun fill(): Color {
            val image = onNodeWithTag("metronome.tap").captureToImage()
            val inset = (12 * pixelDensity).roundToInt()
            return image.averageColorAt(inset, inset)
        }
        val rest = fill()
        emit(source, HoverInteraction.Enter())
        val hovered = fill()
        assertTrue(hovered.luminance() > rest.luminance() + 0.005f, "hover did not raise the tap card: $rest -> $hovered")
    }

    @Test
    fun theSliderThumbShowsHoverAndFocus() = runComposeUiTest {
        val source = MutableInteractionSource()
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                pixelDensity = LocalDensity.current.density
                // An opaque surface: Android captures a transparent node over the activity's white window.
                Box(Modifier.width(300.dp).background(TembraTheme.colors.surface)) {
                    TempoSlider(tempo = Tempo(140), onTempo = {}, interactionSource = source)
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
        fun capture() = onNodeWithTag("metronome.slider").captureToImage()
        // A state layer is a ring of the thumb's own diameter or more: at least a few hundred pixels.
        val minimum = (60 * pixelDensity * pixelDensity).roundToInt()
        val rest = capture()
        val enter = HoverInteraction.Enter()
        emit(source, enter)
        val hovered = changedTowardsPrimary(rest, capture())
        assertTrue(hovered > minimum, "hover drew no halo: $hovered pixels moved towards primary (want > $minimum)")
        emit(source, HoverInteraction.Exit(enter))
        emit(source, FocusInteraction.Focus())
        val focused = changedTowardsPrimary(rest, capture())
        assertTrue(focused > minimum, "focus drew no ring: $focused pixels moved towards primary (want > $minimum)")
    }

    @Test
    fun thePlayButtonUsesTheFilledGlyphs() = runComposeUiTest {
        setContent {
            TembraTheme {
                PlayPauseButton(running = false, onToggle = {})
            }
        }
        onNodeWithTag("metronome.play.icon.PlayArrowFilled", useUnmergedTree = true).assertExists()
    }
}
