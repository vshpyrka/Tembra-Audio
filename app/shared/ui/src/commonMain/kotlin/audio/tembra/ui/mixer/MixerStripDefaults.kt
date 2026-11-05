package audio.tembra.ui.mixer

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The strip's fixed sizes (feature 012, research R2; spec FR-025): 72 dp wide, 36 dp toggles, 20 dp label rows, the tint band. */
internal object MixerStripDefaults {
    val Width: Dp = 72.dp
    val ToggleSize: Dp = 36.dp
    val LabelHeight: Dp = 20.dp
    val BandHeight: Dp = 4.dp
}
