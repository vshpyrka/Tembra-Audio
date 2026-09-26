package audio.tembra.ui.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Average colour of the (2 × [radius] + 1)² block centred on ([x], [y]) in pixels, clamped to the image (analysis L6). */
internal fun ImageBitmap.averageColorAt(x: Int, y: Int, radius: Int = 1): Color {
    val pixels = toPixelMap()
    var r = 0f
    var g = 0f
    var b = 0f
    var a = 0f
    var count = 0
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            val px = (x + dx).coerceIn(0, pixels.width - 1)
            val py = (y + dy).coerceIn(0, pixels.height - 1)
            val p = pixels[px, py]
            r += p.red
            g += p.green
            b += p.blue
            a += p.alpha
            count++
        }
    }
    return Color(r / count, g / count, b / count, a / count)
}

/** Average colour of the whole image. */
internal fun ImageBitmap.averageColor(): Color {
    val pixels = toPixelMap()
    var r = 0f
    var g = 0f
    var b = 0f
    var a = 0f
    val count = pixels.width * pixels.height
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            val p = pixels[x, y]
            r += p.red
            g += p.green
            b += p.blue
            a += p.alpha
        }
    }
    return Color(r / count, g / count, b / count, a / count)
}

/** Average of a block at a dp position inside the node's own capture (the node's top-left is the image origin). */
internal fun SemanticsNodeInteraction.averageColorAt(image: ImageBitmap, position: DpOffset, density: Density, radius: Int = 1): Color {
    val bounds = getBoundsInRoot()
    val x = with(density) { (position.x - bounds.left).toPx() }.roundToInt()
    val y = with(density) { (position.y - bounds.top).toPx() }.roundToInt()
    return image.averageColorAt(x, y, radius)
}

/** Relative luminance, enough to say which of two colours is brighter. */
internal fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Euclidean distance in RGB, ignoring alpha. */
internal fun Color.distanceTo(other: Color): Float {
    val dr = red - other.red
    val dg = green - other.green
    val db = blue - other.blue
    return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
}

/** True when this colour is closer to [tint] than to [other]: a pixel block "tinted like" one of two candidates. */
internal fun Color.isCloserTo(tint: Color, other: Color): Boolean = distanceTo(tint) < distanceTo(other)

/** Blue minus red: positive for the lavender `primary` (also when dimmed over a dark surface), near zero for the greys. */
internal fun Color.violetness(): Float = blue - red

/** True when every channel is within [tolerance] of [other]. */
internal fun Color.approximately(other: Color, tolerance: Float = 0.02f): Boolean =
    abs(red - other.red) <= tolerance && abs(green - other.green) <= tolerance && abs(blue - other.blue) <= tolerance

/** Centre of a rect in root coordinates as a DpOffset. */
internal fun Rect.centerOffset(density: Density): DpOffset = with(density) { DpOffset(center.x.toDp(), center.y.toDp()) }
