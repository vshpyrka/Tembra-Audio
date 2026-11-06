package audio.tembra.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pan knob's fixed sizes (feature 012, research R2, R3): a 40 dp dial, 2 dp of drag per percent. */
internal object PanKnobDefaults {
    val Size: Dp = 40.dp
    val ArcStroke: Dp = 3.dp
    val PointerStroke: Dp = 2.dp

    /** A vertical drag turns the knob one percent per this distance (research R3). */
    val DpPerPercent: Dp = 2.dp
}
