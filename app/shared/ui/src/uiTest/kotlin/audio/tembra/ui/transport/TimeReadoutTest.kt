package audio.tembra.ui.transport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import kotlin.test.Test
import kotlin.test.assertTrue

/** The readout measures itself at its widest text and shrinks to [TimeReadout]'s `maxWidth` when the font would not fit (spec FR-004, FR-059). */
@OptIn(ExperimentalTestApi::class)
class TimeReadoutTest {

    private fun widthOf(maxWidth: Dp, stacked: Boolean, fontScale: Float): Dp {
        var width = 0.dp
        runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                TembraTheme {
                    // Wider than any readout: the node is never clamped by its parent, so its width is what it asked for.
                    Box(Modifier.width(400.dp)) {
                        TimeReadout(positionSeconds = 0.0, lengthSeconds = 30.9677, playing = false, telemetry = ScriptedTransportTelemetry.stopped(0.0), stacked = stacked, maxWidth = maxWidth)
                    }
                }
            }
        }
        val bounds = onNodeWithTag(READOUT_TAG).getUnclippedBoundsInRoot()
        width = bounds.right - bounds.left
        }
        return width
    }

    @Test
    fun theOneLineReadoutIsWiderThanAHundredDpAndShrinksToItsRoom() {
        val free = widthOf(Dp.Unspecified, stacked = false, fontScale = 1f)
        assertTrue(free > 100.dp, "the one-line readout at its own size: $free")
        val fitted = widthOf(100.dp, stacked = false, fontScale = 1f) // above the half-size floor (the one-line text is about 150 dp)
        assertTrue(fitted <= 100.dp + PIXEL_ROUNDING, "the one-line readout given 100 dp: $fitted")
    }

    @Test
    fun theStackedReadoutAtTwiceTheFontShrinksToItsRoom() {
        val free = widthOf(Dp.Unspecified, stacked = true, fontScale = 2f)
        assertTrue(free > 80.dp, "the stacked readout at twice the font: $free")
        val fitted = widthOf(70.dp, stacked = true, fontScale = 2f)
        assertTrue(fitted <= 70.dp + PIXEL_ROUNDING, "the stacked readout given 70 dp: $fitted")
        assertTrue(fitted >= 35.dp, "the digits never shrink below half: $fitted")
    }

    private companion object {
        /** The room is laid out in whole pixels: 70 dp is 171 px at density 2.4375 (the fold emulator), 70.15 dp back. */
        val PIXEL_ROUNDING = 1.dp
    }
}
