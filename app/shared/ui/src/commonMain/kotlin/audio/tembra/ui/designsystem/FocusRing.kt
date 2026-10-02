package audio.tembra.ui.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The keyboard focus ring of the chips and pills: a [width] stroke inset inside the drawn shape of corner [radius]. */
internal fun DrawScope.drawFocusRing(color: Color, width: Float, radius: CornerRadius) {
    drawRoundRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(radius.x - width / 2),
        style = Stroke(width = width),
    )
}
