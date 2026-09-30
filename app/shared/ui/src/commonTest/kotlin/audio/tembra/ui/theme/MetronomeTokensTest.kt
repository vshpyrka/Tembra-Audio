package audio.tembra.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the metronome's screen-derived tokens to data-model.md §9 and research R10 of feature 004. */
class MetronomeTokensTest {

    @Test
    fun typeScaleCarriesTheMetronomeStyles() {
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 64f, 64f, 700, -0.05f, tabularFigures = true), TembraTypeScale.tempoNumber)
        assertEquals(TembraTextStyleSpec(TembraFont.SpaceGrotesk, 10f, 14f, 600, 0.05f), TembraTypeScale.sectionLabel)
    }

    @Test
    fun metronomeStylesStayOutOfTheThemeJsonMap() {
        val themeStyles = TembraTypeScale.byThemeName.values
        assertTrue(TembraTypeScale.tempoNumber !in themeStyles, "tempoNumber is listed as a theme.json style")
        assertTrue(TembraTypeScale.sectionLabel !in themeStyles, "sectionLabel is listed as a theme.json style")
        assertEquals(12, TembraTypeScale.byThemeName.size)
    }

    @Test
    fun spacingCarriesTheMetronomeMetrics() {
        val spacing = TembraSpacing()
        assertEquals(32.dp, spacing.chipHeight)
        assertEquals(28.dp, spacing.pillHeight)
        assertEquals(64.dp, spacing.accentCellBarHeight)
        assertEquals(270.dp, spacing.tempoDialMaxWidth)
        assertEquals(420.dp, spacing.tempoDialMaxWidthWide)
        assertEquals(44.dp, spacing.tempoStepper)
        assertEquals(64.dp, spacing.tapTempoHeight)
        assertEquals(64.dp, spacing.playButton)
        assertEquals(160.dp, spacing.soundMenuWidth)
        assertEquals(44.dp, spacing.soundTileHeight)
        assertEquals(28.dp, spacing.soundTileGlyphBox)
        assertEquals(320.dp, spacing.wideSettingsMinWidth)
    }

    @Test
    fun gradientsMatchTheMetronomeScreens() {
        val gradients = TembraGradients.Default
        assertEquals(
            LinearGlow(
                start = Offset(0f, 0f),
                end = Offset(1f, 1f),
                stops = listOf(0f to Color(0xFFCEBDFF), 0.5f to Color(0xFFA78BFA), 1f to Color(0xFFFFAFD3)),
            ),
            gradients.tempoArc,
        )
        assertEquals(
            RadialGlow(Offset(0.5f, 0.5f), listOf(0f to Color(0x14A78BFA), 0.4f to Color(0x661E1B2E), 1f to Color(0xF2131316))),
            gradients.tempoDialCard,
        )
        assertEquals(
            LinearGlow(Offset(0.5f, 0f), Offset(0.5f, 1f), listOf(0f to Color(0x1ACEBDFF), 0.5f to Color.Transparent, 1f to Color(0x1AFFAFD3))),
            gradients.tempoDialOverlay,
        )
        assertEquals(Glow(Color(0x80FFAFD3), 12.dp), gradients.accentGlow)
        assertEquals(Glow(Color(0x59FFAFD3), 24.dp), gradients.playGlow)
    }

    @Test
    fun everyMetronomeGradientStopIsASchemeColourOrTheDocumentedInGradientStop() {
        val gradients = TembraGradients.Default
        val stops = gradients.tempoArc.stops.map { it.second } +
            gradients.tempoDialCard.stops.map { it.second } +
            gradients.tempoDialOverlay.stops.map { it.second } +
            listOf(gradients.accentGlow.color, gradients.playGlow.color)
        val allowed = schemeColors() + IN_GRADIENT_STOP
        for (stop in stops.filter { it.alpha > 0f }) {
            val opaque = stop.copy(alpha = 1f)
            assertTrue(opaque in allowed, "gradient stop $stop is neither a TembraColorScheme.dark colour nor #1E1B2E")
        }
    }

    @Test
    fun iconRegistryCarriesTheMetronomeGlyphs() {
        val names = TembraIcon.entries.map { it.name }
        for (expected in listOf("Remove", "Add", "TouchApp", "MusicNote", "GraphicEq", "RadioButtonChecked", "Notifications", "Sensors", "Tune", "Check", "PlayArrowFilled", "PauseFilled", "SwapHoriz")) {
            assertTrue(expected in names, "TembraIcon.$expected is missing")
        }
    }

    private fun schemeColors(): Set<Color> = with(TembraColorScheme.dark) {
        setOf(
            primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
            secondary, onSecondary, secondaryContainer, onSecondaryContainer,
            tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
            background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint,
            inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
            outline, outlineVariant, scrim, surfaceBright, surfaceDim,
            surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
        )
    }

    private companion object {
        /** The dial card's radial midpoint `rgba(30,27,46,0.4)`, an in-gradient stop like the tuner's `#D946EF` (research R10). */
        val IN_GRADIENT_STOP = Color(0xFF1E1B2E)
    }
}
