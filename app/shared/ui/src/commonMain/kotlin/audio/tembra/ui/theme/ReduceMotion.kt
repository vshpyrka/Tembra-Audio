package audio.tembra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale

/**
 * True when the platform asks for reduced motion. Compose injects a [MotionDurationScale] into the composition on both
 * platforms: Android mirrors the "Remove animations" setting, Compose Multiplatform on iOS mirrors
 * `UIAccessibilityIsReduceMotionEnabled()` (research R7). A scale of 0 means every animation completes on the next frame.
 */
@Composable
fun rememberReduceMotion(): Boolean =
    rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
