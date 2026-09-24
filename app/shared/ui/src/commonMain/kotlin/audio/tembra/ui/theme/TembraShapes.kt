package audio.tembra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from design/stitch/theme.json → rounded (1rem = 16 dp); `full` is a pill. */
object TembraShapes {
    val small = 4.dp
    val default = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp

    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(small),
        small = RoundedCornerShape(default),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        extraLarge = RoundedCornerShape(extraLarge),
    )
}
