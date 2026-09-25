package audio.tembra.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens (data-model.md §5). [standardDuration] is Material 3 `duration.medium2` (300 ms), the standard for a
 * full-screen content change; [standardEasing] is Material 3 `easing.standard`. [reduceMotion] mirrors the platform's
 * reduce-motion preference and is provided at the root by [TembraTheme]; no screen defines its own duration (spec FR-008).
 */
@Immutable
data class TembraMotion(
    val standardDuration: Int = 300,
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val reduceMotion: Boolean = false,
) {
    /** 0 under reduce motion, else [standardDuration]; every animation in the app reads this. */
    val effectiveDuration: Int
        get() = if (reduceMotion) 0 else standardDuration
}
