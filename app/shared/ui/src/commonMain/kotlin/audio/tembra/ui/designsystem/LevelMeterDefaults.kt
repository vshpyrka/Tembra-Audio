package audio.tembra.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The level meter's fixed sizes (feature 012, research R2): two 6 dp bars 2 dp apart under a clip square as wide as the meter. */
internal object LevelMeterDefaults {
    val BarWidth: Dp = 6.dp
    val Gap: Dp = 2.dp

    /** The meter's width: both bars and the gap; also the clip square's side. */
    val Width: Dp = BarWidth * 2 + Gap

    /** The hold mark's thickness. */
    val MarkHeight: Dp = 2.dp

    /** The clip square's touch target, wider than the drawn square so a finger finds it. */
    val ClipTouch: Dp = 24.dp
}
