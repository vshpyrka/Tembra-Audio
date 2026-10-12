package audio.tembra.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the tuner's screen-derived tokens to the values of data-model.md §8 and research R9. */
class TunerTokensTest {

    @Test
    fun typeScaleCarriesTheTunerStyles() {
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 64f, 64f, 700, -0.02f), TembraTypeScale.tunerNote)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 32f, 32f, 700), TembraTypeScale.tunerOctave)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 32f, 32f, 700), TembraTypeScale.tunerCardNote)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 20f, 20f, 700), TembraTypeScale.tunerCardOctave)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 18f, 24f, 600, 0.05f, tabularFigures = true), TembraTypeScale.tunerReadout)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 11f, 14f, 600, 0.04f), TembraTypeScale.microLabel)
    }

    @Test
    fun spacingCarriesTheTunerMetrics() {
        val spacing = TembraSpacing()
        assertEquals(32.dp, spacing.segmentHeightLarge)
        assertEquals(28.dp, spacing.segmentHeightSmall)
        assertEquals(340.dp, spacing.meterMaxWidth)
        assertEquals(480.dp, spacing.meterMaxWidthWide)
        assertEquals(384.dp, spacing.byEarGridMaxWidth)
        assertEquals(576.dp, spacing.byEarGridMaxWidthWide)
        assertEquals(128.dp, spacing.stringCardMinWide)
        assertEquals(128.dp, spacing.stringCardHeightLandscape)
        assertEquals(224.dp, spacing.landscapeSettingsMinWidth)
        assertEquals(280.dp, spacing.landscapeSettingsMaxWidth)
        assertEquals(200.dp, spacing.meterMinWidth)
        assertEquals(32.dp, spacing.toggleWidth)
        assertEquals(16.dp, spacing.toggleHeight)
        assertEquals(12.dp, spacing.toggleKnob)
        assertEquals(6.dp, spacing.statusDot)
    }

    @Test
    fun gradientsMatchTheStitchScreens() {
        val gradients = TembraGradients.Default
        assertEquals(
            RadialGlow(center = Offset(0.6f, 0.45f), stops = listOf(0f to Color(0x1FA78BFA), 0.35f to Color(0x0FD946EF), 0.7f to Color.Transparent)),
            gradients.meterCompact,
        )
        assertEquals(
            RadialGlow(center = Offset(0.5f, 0.3f), stops = listOf(0f to Color(0x0FAB88FF), 0.4f to Color(0x0F85145A), 0.75f to Color.Transparent)),
            gradients.byEarCompact,
        )
        assertEquals(
            RadialGlow(center = Offset(0.6f, 0.5f), stops = listOf(0f to Color(0x1AA78BFA), 0.45f to Color(0x1485145A), 0.75f to Color.Transparent)),
            gradients.byEarWide,
        )
        assertEquals(Glow(Color(0xB3FFAFD3), 8.dp), gradients.needleGlow)
        assertEquals(Glow(Color(0x6685145A), 18.dp), gradients.playingGlow)
        assertEquals(112.dp, gradients.discRadius)
        assertEquals(64.dp, gradients.discOffset)
        assertEquals(Color(0x1AA78BFA), gradients.discTopStart)
        assertEquals(Color(0x2685145A), gradients.discBottomEnd)
    }

    @Test
    fun iconRegistryCarriesTheTunerGlyphs() {
        val names = TembraIcon.entries.map { it.name }
        for (expected in listOf("Mic", "MicOff", "Hearing", "PlayArrow", "Pause", "VolumeUp", "ExpandMore", "ArrowLeft", "ArrowRight", "Settings")) {
            assertTrue(expected in names, "TembraIcon.$expected is missing")
        }
    }
}
