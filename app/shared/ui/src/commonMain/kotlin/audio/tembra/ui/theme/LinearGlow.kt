package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A CSS `linear-gradient` as data, beside [RadialGlow]: [start] and [end] in fractions of the drawn size, [stops] as
 * fraction to colour (research R10 of feature 004). Drawn with [brush].
 */
@Immutable
data class LinearGlow(val start: Offset, val end: Offset, val stops: List<Pair<Float, Color>>) {
    fun brush(size: Size): Brush = Brush.linearGradient(
        colorStops = stops.toTypedArray(),
        start = Offset(start.x * size.width, start.y * size.height),
        end = Offset(end.x * size.width, end.y * size.height),
    )
}
