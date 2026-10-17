package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** JetBrains' port of 1.5.0-alpha22 still has the value-based slider with a custom thumb and track. */
@Composable
internal actual fun TembraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    interactionSource: MutableInteractionSource,
    thumb: @Composable (SliderState) -> Unit,
    track: @Composable (SliderState) -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track,
    )
}
