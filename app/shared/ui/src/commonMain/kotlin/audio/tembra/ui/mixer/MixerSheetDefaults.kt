package audio.tembra.ui.mixer

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The sheet's fixed sizes (feature 012, contracts/mixer-ui.md): the 32 dp handle row and the grabber drawn in it. */
internal object MixerSheetDefaults {
    val HandleHeight: Dp = 32.dp
    val GrabberWidth: Dp = 32.dp
    val GrabberHeight: Dp = 4.dp

    /** A release past this share of the way to the next anchor lands on it. */
    const val POSITIONAL_THRESHOLD = 0.5f
}
