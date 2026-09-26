package audio.tembra.ui.tuner

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * A frame tick that only the meter's draw lambda reads (research R8): while [active], every display frame writes the
 * frame time into the returned state, invalidating the draw phase alone; nothing recomposes. The loop runs on the
 * infinite-animation frame clock so UI tests can pause and step it like any other endless animation.
 */
@Composable
fun rememberFrameTick(active: Boolean): LongState {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        while (active) {
            withInfiniteAnimationFrameNanos { tick.longValue = it }
        }
    }
    return tick
}
