package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.LinearGlow
import audio.tembra.ui.theme.RadialGlow
import audio.tembra.ui.theme.TembraGradients
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rounded gradient card behind the meter and the By Ear grid (research R9) and, from feature 004, the tempo dial
 * (research R10): disc placement and the vertical overlay. Pixel probes run with the clock frozen.
 */
@OptIn(ExperimentalTestApi::class)
class TembraGradientCardTest {

    private var lowest: Color = Color.Unspecified
    private var pixelDensity = 1f

    @Test
    fun rendersContentWithTheGlowAndAnOpaqueBody() = runComposeUiTest {
        setContent {
            TembraTheme {
                lowest = TembraTheme.colors.surfaceContainerLowest
                pixelDensity = LocalDensity.current.density
                TembraGradientCard(glow = TembraGradients.Default.meterCompact, testTag = "card", modifier = Modifier.size(320.dp, 240.dp)) {
                    Text("inside")
                }
            }
        }
        onNodeWithText("inside").assertIsDisplayed()
        onNodeWithTag("card").assertIsDisplayed()
        val image = onNodeWithTag("card").captureToImage()
        val centre = image.averageColorAt(image.width * 6 / 10, image.height * 45 / 100)
        assertFalse(centre.approximately(lowest), "the glow is not drawn: centre $centre equals surfaceContainerLowest $lowest")
        val inset = (24 * pixelDensity).roundToInt()
        val corner = image.averageColorAt(inset, inset)
        assertTrue(corner.alpha > 0.99f, "the body near the corner is transparent: $corner")
    }

    private var secondaryContainer: Color = Color.Unspecified
    private var primaryContainer: Color = Color.Unspecified

    /** One 320 × 240 card whose [discs] and [overlay] the test switches between captures. */
    private fun ComposeUiTest.setSwitchableCard(glow: RadialGlow, initialDiscs: CardDiscs, initialOverlay: LinearGlow? = null): (CardDiscs, LinearGlow?) -> ImageBitmap {
        var discs by mutableStateOf(initialDiscs)
        var overlay by mutableStateOf(initialOverlay)
        setContent {
            TembraTheme {
                lowest = TembraTheme.colors.surfaceContainerLowest
                secondaryContainer = TembraTheme.colors.secondaryContainer
                primaryContainer = TembraTheme.colors.primaryContainer
                pixelDensity = LocalDensity.current.density
                TembraGradientCard(glow = glow, modifier = Modifier.size(320.dp, 240.dp), discs = discs, overlay = overlay, testTag = "card") {}
            }
        }
        mainClock.autoAdvance = false
        return { newDiscs, newOverlay ->
            discs = newDiscs
            overlay = newOverlay
            mainClock.advanceTimeByFrame()
            onNodeWithTag("card").captureToImage()
        }
    }

    /** 3 × 3 average [CORNER_PROBE_DP] in from both edges of a corner: inside the 16 dp rounding and clear of the seam. */
    private fun ImageBitmap.corner(right: Boolean, bottom: Boolean): Color {
        val inset = (CORNER_PROBE_DP * pixelDensity).roundToInt()
        return averageColorAt(if (right) width - 1 - inset else inset, if (bottom) height - 1 - inset else inset)
    }

    @Test
    fun mirroredDiscsTintTheTopEndSecondaryAndTheBottomStartPrimary() = runComposeUiTest {
        val capture = setSwitchableCard(TRANSPARENT_GLOW, CardDiscs.TopStartBottomEnd)
        val tuner = capture(CardDiscs.TopStartBottomEnd, null)
        val dial = capture(CardDiscs.TopEndBottomStart, null)
        val topEndTuner = tuner.corner(right = true, bottom = false)
        val topEndDial = dial.corner(right = true, bottom = false)
        assertTrue(topEndDial.red > topEndTuner.red, "no secondaryContainer disc at the top end: $topEndTuner -> $topEndDial")
        assertTrue(topEndDial.distanceTo(secondaryContainer) < topEndTuner.distanceTo(secondaryContainer), "the top end did not move towards secondaryContainer")
        val bottomStartTuner = tuner.corner(right = false, bottom = true)
        val bottomStartDial = dial.corner(right = false, bottom = true)
        assertTrue(bottomStartDial.blue > bottomStartTuner.blue, "no primaryContainer disc at the bottom start: $bottomStartTuner -> $bottomStartDial")
        assertTrue(bottomStartDial.distanceTo(primaryContainer) < bottomStartTuner.distanceTo(primaryContainer), "the bottom start did not move towards primaryContainer")
    }

    @Test
    fun noDiscsLeaveEveryCornerAtTheGlowColour() = runComposeUiTest {
        val capture = setSwitchableCard(TRANSPARENT_GLOW, CardDiscs.None)
        val none = capture(CardDiscs.None, null)
        for (right in listOf(false, true)) {
            for (bottom in listOf(false, true)) {
                val corner = none.corner(right, bottom)
                assertTrue(corner.approximately(lowest, tolerance = 1f / 255f), "corner right=$right bottom=$bottom is $corner, not the bare card $lowest")
            }
        }
        val tuner = capture(CardDiscs.TopStartBottomEnd, null)
        assertTrue(tuner.corner(right = false, bottom = false).blue > none.corner(right = false, bottom = false).blue, "the tuner's top-start disc is missing")
    }

    @Test
    fun theOverlayShadesTopAndBottomDifferently() = runComposeUiTest {
        val capture = setSwitchableCard(TembraGradients.Default.tempoDialCard, CardDiscs.None)
        val plain = capture(CardDiscs.None, null)
        val inset = (CORNER_PROBE_DP * pixelDensity).roundToInt()
        val plainTop = plain.averageColorAt(plain.width / 2, inset)
        val plainBottom = plain.averageColorAt(plain.width / 2, plain.height - 1 - inset)
        assertTrue(plainTop.approximately(plainBottom, tolerance = 2f / 255f), "the radial glow alone is not vertically symmetric: $plainTop vs $plainBottom")
        val shaded = capture(CardDiscs.None, TembraGradients.Default.tempoDialOverlay)
        val top = shaded.averageColorAt(shaded.width / 2, inset)
        val bottom = shaded.averageColorAt(shaded.width / 2, shaded.height - 1 - inset)
        assertFalse(top.approximately(bottom, tolerance = 2f / 255f), "the overlay does not shade top ($top) and bottom ($bottom) differently")
    }

    private companion object {
        const val CORNER_PROBE_DP = 7
        /** A glow that draws nothing, so a corner shows only the card body and its discs. */
        val TRANSPARENT_GLOW = RadialGlow(Offset(0.5f, 0.5f), listOf(0f to Color.Transparent, 1f to Color.Transparent))
    }
}
