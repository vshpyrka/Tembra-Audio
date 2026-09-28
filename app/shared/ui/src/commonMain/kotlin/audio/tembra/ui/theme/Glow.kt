package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** A soft glow drawn as translucent strokes or layered shapes (never a shadow): its colour and reach. */
@Immutable
data class Glow(val color: Color, val radius: Dp)
