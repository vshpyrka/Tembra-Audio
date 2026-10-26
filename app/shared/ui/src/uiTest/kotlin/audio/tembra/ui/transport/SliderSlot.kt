package audio.tembra.ui.transport

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/**
 * Whether a slider's node bounds come from a slot [width] wide. Material 1.5's slider lays its node out wider than the
 * slot it is given, by the room of its inset focus ring on each side (10 dp a side on the Android device), so the node
 * is at least the slot and at most [SliderFocusRingRoom] wider.
 */
internal fun DpRect.isSliderSlotOf(width: Dp): Boolean = (right - left) >= width - 0.5.dp && (right - left) <= width + SliderFocusRingRoom

/** Both sides' focus-ring room, with some to spare. */
private val SliderFocusRingRoom = 24.dp
