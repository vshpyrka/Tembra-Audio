package audio.tembra.ui.designsystem

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.TextAutoSizeDefaults
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * [TextAutoSize.StepBased] that tries the largest size first. StepBased bisects the range from its middle, so a text
 * that fits at its largest size is still laid out seven to nine times; on a fresh Metronome its seven auto-sized texts
 * spent 2 to 5 ms each that way inside one frame. This lays the text out once at the largest step and keeps that size
 * when nothing overflows and no line is ellipsized; otherwise StepBased decides. Either way the size is the one
 * StepBased would choose: the largest step from [minFontSize] that is not above [maxFontSize] and fits.
 */
fun stepBasedFitFirst(
    minFontSize: TextUnit = TextAutoSizeDefaults.MinFontSize,
    maxFontSize: TextUnit = TextAutoSizeDefaults.MaxFontSize,
    stepSize: TextUnit = 0.25.sp,
): TextAutoSize = FitFirstAutoSize(minFontSize, maxFontSize, stepSize)

private class FitFirstAutoSize(
    private val minFontSize: TextUnit,
    private val maxFontSize: TextUnit,
    private val stepSize: TextUnit,
) : TextAutoSize {
    private val stepBased = TextAutoSize.StepBased(minFontSize, maxFontSize, stepSize)

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val min = minFontSize.toPx()
        val step = stepSize.toPx()
        val largest = (min + floor((maxFontSize.toPx() - min) / step) * step).toSp()
        val layout = performLayout(constraints, text, largest)
        val fits = !layout.didOverflowWidth && !layout.didOverflowHeight &&
            (0 until layout.lineCount).none { layout.isLineEllipsized(it) }
        return if (fits) largest else with(stepBased) { getFontSize(constraints, text) }
    }

    override fun equals(other: Any?): Boolean = other is FitFirstAutoSize &&
        other.minFontSize == minFontSize && other.maxFontSize == maxFontSize && other.stepSize == stepSize

    override fun hashCode(): Int = (minFontSize.hashCode() * 31 + maxFontSize.hashCode()) * 31 + stepSize.hashCode()
}
