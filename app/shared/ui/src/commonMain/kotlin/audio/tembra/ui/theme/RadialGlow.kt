package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * A CSS `radial-gradient(circle at x% y%, …)` as data: [center] in fractions of the drawn size, [stops] as fraction
 * to colour. Drawn with [brush]; never with `Modifier.blur` (research R9).
 */
@Immutable
data class RadialGlow(val center: Offset, val stops: List<Pair<Float, Color>>) {
    fun brush(size: Size): Brush = Brush.radialGradient(
        colorStops = stops.toTypedArray(),
        center = Offset(center.x * size.width, center.y * size.height),
        radius = max(size.width, size.height),
    )
}
