package audio.tembra.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fader's fixed sizes (feature 012, research R2; spec FR-018, FR-025): the 44 × 28 dp handle, the rail, the marks and the least throw. */
internal object FaderDefaults {
    val HandleWidth: Dp = 44.dp
    val HandleHeight: Dp = 28.dp
    val RailWidth: Dp = 4.dp
    val TickLength: Dp = 4.dp
    val TickGap: Dp = 2.dp

    /**
     * The least height of a fader at either sheet height, the handle included (spec FR-018's "throw", counted as research
     * R2 counts it): the strip drops its dial and knob before the fader falls under it (FR-025).
     */
    val MinThrow: Dp = 120.dp

    /** From this width the scale marks carry their labels; narrower faders draw the marks alone. */
    val LabelledFrom: Dp = 64.dp
}
