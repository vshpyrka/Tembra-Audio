package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlin.math.abs

/**
 * Material's slider with the design system's own [thumb] and [track]. Material 1.5 moves a horizontal slider with Left
 * and Right only, where 1.4 also took Up and Down; the design's sliders answer every arrow key (feature 004's keyboard
 * scenario, feature 006 FR-018), so Up and Down move one step here when Material leaves them unhandled, and their
 * release ends the change as Material's keys do.
 */
@Composable
internal fun TembraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource,
    thumb: @Composable (SliderState) -> Unit,
    track: @Composable (SliderState) -> Unit,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    // Material's keyboard step: one of the steps when the slider has them, else a hundredth of the range.
    val step = abs(valueRange.endInclusive - valueRange.start) / (if (steps > 0) steps + 1 else 100)
    PlatformSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onKeyEvent { event ->
            val direction = when (event.key) {
                Key.DirectionUp, Key.NumPadDirectionUp -> 1
                Key.DirectionDown, Key.NumPadDirectionDown -> -1
                else -> 0
            }
            when {
                direction == 0 -> false
                event.type == KeyEventType.KeyDown -> {
                    currentOnValueChange((currentValue + direction * step).coerceIn(valueRange))
                    true
                }
                event.type == KeyEventType.KeyUp -> {
                    currentOnValueChangeFinished?.invoke()
                    true
                }
                else -> false
            }
        },
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track,
    )
}

/**
 * The platform's Material slider: Android runs androidx material3 1.5.0-beta01, which drops the value-based slider with
 * a custom thumb and track in favour of `Slider(state, onValueChange, …)`, while iOS runs JetBrains' port of
 * 1.5.0-alpha22, which has only the older forms, so each platform calls its own.
 */
@Composable
internal expect fun PlatformSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    interactionSource: MutableInteractionSource,
    thumb: @Composable (SliderState) -> Unit,
    track: @Composable (SliderState) -> Unit,
)
