package audio.tembra.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.ui.designsystem.TembraSlider
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The master volume (spec FR-009): a Material slider over 0–100 % in whole steps on the design system's 6 dp track and
 * 16 dp `primary` handle ([TembraSliderThumb], as the tempo slider and the noise gate). Arrows move it 1 %, the page
 * keys 10 %; it reads "Master volume, 80 percent" and keeps Material's range semantics for `setProgress`. Changes
 * reach [onVolume] while dragging, so the engine's 10 ms ramp follows the finger (SC-006).
 */
@Composable
internal fun MasterVolumeSlider(
    percent: Int,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = TAG,
) {
    val colors = TembraTheme.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val current by rememberUpdatedState(percent)
    val currentOnVolume by rememberUpdatedState(onVolume)

    fun offer(next: Int) {
        val clamped = next.coerceIn(0, 100)
        if (clamped != current) currentOnVolume(clamped)
    }

    TembraSlider(
        value = percent.toFloat(),
        onValueChange = { offer(it.roundToInt()) },
        modifier = modifier
            .testTag(testTag)
            .semantics {
                contentDescription = TransportCopy.masterVolume
                stateDescription = TransportCopy.masterVolumeState(percent)
            }
            .onPreviewKeyEvent { event ->
                val delta = when {
                    event.type != KeyEventType.KeyDown -> 0
                    event.key == Key.PageUp -> PAGE_STEP
                    event.key == Key.PageDown -> -PAGE_STEP
                    else -> 0
                }
                if (delta != 0) offer(current + delta)
                delta != 0
            },
        valueRange = 0f..100f,
        steps = 99,
        interactionSource = source,
        thumb = { TembraSliderThumb(source) },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TembraSliderDefaults.TrackHeight)
                    .background(colors.surfaceContainerHighest, CircleShape),
            )
        },
    )
}

internal const val TAG = "transport.master"

/** The page keys move the volume by ten, the arrows by one. */
private const val PAGE_STEP = 10
