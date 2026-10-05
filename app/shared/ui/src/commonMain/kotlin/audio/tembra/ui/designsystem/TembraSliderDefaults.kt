package audio.tembra.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the app's sliders share (the metronome's tempo slider, feature 004 research R10, and the tuner's noise gate,
 * feature 006 research R9): the 6 dp track and the 16 dp handle with its state layer.
 */
internal object TembraSliderDefaults {
    val TrackHeight: Dp = 6.dp
    val ThumbSize: Dp = 16.dp

    /** The handle's state layer on hover, press, drag and focus: `primary` at 16 %, twice the handle's diameter. */
    const val THUMB_HALO_ALPHA = 0.16f
    const val THUMB_HALO_SCALE = 2f
}
