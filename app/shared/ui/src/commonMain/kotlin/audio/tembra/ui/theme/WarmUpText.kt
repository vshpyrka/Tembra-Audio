package audio.tembra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Takes the first-use cost of text out of the first frame of a screen, once the first frame of the app is up. Without
 * it the first screen that uses a face or a size it has not seen yet (the Metronome's numerals, the Tuner's note) did
 * all of this inside its first frame on a Pixel 6 Pro: a quarter of that frame went to reading the font files and 1 to
 * 5 ms to each new style's first layout.
 * 1. Every bundled face is loaded into the font resolver's process-wide cache, off the main thread.
 * 2. Every theme style lays out [GLYPHS], off the main thread too; the caches it fills are the process's.
 * Rasterising every glyph of every style ahead as well (until 2026-10-20) made no measurable difference once the screens
 * themselves are drawn ahead (DestinationsDrawnAhead), and it filled the render thread's glyph atlas, which the screens'
 * own glyphs then had to evict. [onWarm] follows the last style.
 */
@Composable
internal fun WarmUpText(onWarm: () -> Unit) {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val families = listOf(TembraFonts.inter(), TembraFonts.spaceGrotesk())
    val styles = TembraTheme.typography.styles() + TembraTheme.typographyExtras.styles()
    val warm by rememberUpdatedState(onWarm)
    LaunchedEffect(resolver, density, direction) {
        withFrameNanos { }
        // Off the main thread, so no frame of the launch waits: the first layout of the process alone (locale, ICU,
        // the paragraph builder) took 10 ms on an iPhone 14, and 26 of them back to back held its launch frames.
        withContext(Dispatchers.Default) {
            families.forEach { resolver.preload(it) }
            val measurer = TextMeasurer(resolver, density, direction, cacheSize = 0)
            val width = with(density) { WRAP_WIDTH.roundToPx() }
            for (style in styles) measurer.measure(GLYPHS, style, constraints = Constraints(maxWidth = width))
        }
        warm()
    }
}

/** Every letter, digit and sign the screens print, so each glyph is shaped once per style. */
private const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789 ()[]/-–•#♭♯%+:.,'"

/** A phone's width: long lines wrap, as on the screens. */
private val WRAP_WIDTH = 400.dp

private fun Typography.styles(): List<TextStyle> = listOf(
    displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
    titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
)

private fun TembraTypographyExtras.styles(): List<TextStyle> = listOf(
    headlineLgMobile, tabularTimecode, tabularParam, tunerNote, tunerOctave, tunerCardNote, tunerCardOctave,
    tunerReadout, microLabel, tempoNumber, sectionLabel,
)
