package audio.tembra.ui.projects.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.data.projects.CardOverview
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The card's overview of the mix (spec FR-054): the project's spans drawn in the card's dark well, as bars on compact
 * cards or as a line on expanded ones, each span in the tint of its loudest track; an empty overview leaves the well
 * empty. Decorative: it carries no semantics of its own (the card's description names the project).
 */
@Composable
internal fun ProjectOverviewChart(
    overview: CardOverview,
    asLine: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_HEIGHT,
    testTag: String? = null,
) {
    val daw = TembraTheme.daw
    val tints = daw.trackTints
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(daw.canvasLevel0, RoundedCornerShape(TembraShapes.default))
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clearAndSetSemantics {},
    ) {
        val spans = overview.spans
        if (spans == 0) return@Canvas
        val inset = INSET_DP.dp.toPx()
        val usableHeight = size.height - inset * 2
        val spanWidth = (size.width - inset * 2) / spans
        if (asLine) {
            val stroke = LINE_DP.dp.toPx()
            var previous: Offset? = null
            for (span in 0 until spans) {
                val peak = (overview.peaks[span].toInt() and 0xFF) / PEAK_FULL_SCALE
                val point = Offset(inset + spanWidth * (span + 0.5f), inset + usableHeight * (1f - peak))
                previous?.let { drawLine(tints[(overview.colors[span].toInt() and 0xFF) % tints.size], it, point, strokeWidth = stroke, cap = StrokeCap.Round) }
                previous = point
            }
        } else {
            val gap = spanWidth * BAR_GAP
            for (span in 0 until spans) {
                val peak = (overview.peaks[span].toInt() and 0xFF) / PEAK_FULL_SCALE
                val barHeight = (usableHeight * peak).coerceAtLeast(MIN_BAR_DP.dp.toPx())
                drawRect(
                    color = tints[(overview.colors[span].toInt() and 0xFF) % tints.size],
                    topLeft = Offset(inset + spanWidth * span + gap / 2, inset + usableHeight - barHeight),
                    size = Size(spanWidth - gap, barHeight),
                )
            }
        }
    }
}

private val DEFAULT_HEIGHT = 56.dp
private const val INSET_DP = 6
private const val LINE_DP = 2
private const val MIN_BAR_DP = 2
private const val BAR_GAP = 0.3f
private const val PEAK_FULL_SCALE = 255f
