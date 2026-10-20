package audio.tembra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Takes the first-use cost of text out of the first frame of a screen, once the first frame of the app is up. Without
 * it the first screen that uses a face or a size it has not seen yet (the Metronome's numerals, the Tuner's note) did
 * all of this inside its first frame on a Pixel 6 Pro: a quarter of that frame went to reading the font files, 1 to 5 ms
 * to each new style's first layout, and the render thread rasterised every new glyph while drawing it.
 * 1. Every bundled face is loaded into the font resolver's process-wide cache, off the main thread.
 * 2. Every theme style lays out [GLYPHS] on the main thread, one style per message between frames, so no frame waits.
 * 3. Each layout is drawn into an offscreen layer that is read back once, so its glyphs are rasterised on the render
 *    thread's caches before any screen draws them; the readback is not a window frame.
 */
@Composable
internal fun WarmUpText() {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val families = listOf(TembraFonts.inter(), TembraFonts.spaceGrotesk())
    val styles = TembraTheme.typography.styles() + TembraTheme.typographyExtras.styles()
    val layer = rememberGraphicsLayer()
    LaunchedEffect(resolver, density, direction) {
        withFrameNanos { }
        withContext(Dispatchers.Default) { families.forEach { resolver.preload(it) } }
        val measurer = TextMeasurer(resolver, density, direction, cacheSize = 0)
        val width = with(density) { WRAP_WIDTH.roundToPx() }
        for (style in styles) {
            yield()
            val text = measurer.measure(GLYPHS, style, constraints = Constraints(maxWidth = width))
            layer.record(density, direction, IntSize(width, text.size.height.coerceAtLeast(1))) {
                drawText(text, color = Color.White)
            }
            layer.toImageBitmap()
        }
    }
}

/** Every letter, digit and sign the screens print, so each glyph is shaped and rasterised once per style. */
private const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789 ()[]/-–•#♭♯%+:.,'"

/** A phone's width: long lines wrap, which keeps every readback small. */
private val WRAP_WIDTH = 400.dp

private fun Typography.styles(): List<TextStyle> = listOf(
    displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
    titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
)

private fun TembraTypographyExtras.styles(): List<TextStyle> = listOf(
    headlineLgMobile, tabularTimecode, tabularParam, tunerNote, tunerOctave, tunerCardNote, tunerCardOctave,
    tunerReadout, microLabel, tempoNumber, sectionLabel,
)
