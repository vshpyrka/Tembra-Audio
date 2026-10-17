package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material's slider with the design system's own [thumb] and [track]. Experiment: Android runs androidx material3
 * 1.5.0-beta01, which drops the value-based slider with a custom thumb and track in favour of `Slider(state,
 * onValueChange, …)`, while iOS runs JetBrains' port of 1.5.0-alpha22, which has only the older forms, so each platform
 * calls its own.
 */
@Composable
internal expect fun TembraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource,
    thumb: @Composable (SliderState) -> Unit,
    track: @Composable (SliderState) -> Unit,
)
