package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.engine.MixSnapshot
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tuner.LevelSmoother
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The strip's VU dial (feature 012, spec FR-013; research R6; contracts/mixer-ui.md "VuDial"): a half-dial with the
 * needle on [VuScale], −20 to +3 VU with 0 VU at −18 dBFS, the arc above 0 VU in `meterWarning`. The needle follows the
 * engine's 300 ms rms of strip [index] (the master for [MixSnapshot.MASTER_INDEX]) from the sheet's shared [levels],
 * read every frame in `drawBehind` through [tick] and eased by the 30 ms [LevelSmoother], so it never twitches on a
 * single peak; reduce motion shows the published value as it is. Semantics ("VU", "<n> VU") follow [level], the mirror.
 */
@Composable
internal fun VuDial(
    levels: MixSnapshot,
    index: Int,
    tick: LongState,
    level: State<MeterLevel>,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val reduceMotion = TembraTheme.motion.reduceMotion
    val smoother = remember { LevelSmoother() }
    val arc = colors.surfaceContainerHighest
    val warning = daw.meterWarning
    val needle = colors.onSurface
    val current = level.value
    Box(
        modifier = modifier
            .size(VuDialWidth, VuDialHeight)
            .graphicsLayer()
            .drawBehind {
                val frameNanos = tick.longValue
                val rmsDb = VuScale.rmsDbOf(levels, index)
                val shownDb = if (reduceMotion) rmsDb else smoother.advance(rmsDb, frameNanos)
                val stroke = ARC_STROKE_DP.dp.toPx()
                val pivot = Offset(size.width / 2, size.height - stroke)
                val radius = min(size.width / 2, size.height) - stroke
                // While the sheet slides in, the dial may be measured smaller than its stroke: nothing to draw yet.
                if (radius <= 0f) return@drawBehind
                val topLeft = Offset(pivot.x - radius, pivot.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(arc, START_ANGLE, SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                val warningStart = START_ANGLE + SWEEP * VuScale.fractionOf(0f)
                drawArc(warning, warningStart, START_ANGLE + SWEEP - warningStart, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                val angle = (START_ANGLE + SWEEP * VuScale.fractionOf(VuScale.vuOf(shownDb))) * PI / 180.0
                val tip = Offset(pivot.x + radius * cos(angle).toFloat(), pivot.y + radius * sin(angle).toFloat())
                drawLine(needle, start = pivot, end = tip, strokeWidth = NEEDLE_DP.dp.toPx(), cap = StrokeCap.Round)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = LevelMeterCopy.vu
                stateDescription = VuScale.announce(VuScale.vuOf(current.rmsDb))
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/** The dial's size (research R2): 48 dp wide, a half-dial 28 dp tall. */
internal val VuDialWidth: Dp = 48.dp
internal val VuDialHeight: Dp = 28.dp

/** The arc from the left-up to the right-up, in Compose's degrees (0 at three o'clock, clockwise). */
private const val START_ANGLE = 210f
private const val SWEEP = 120f
private const val ARC_STROKE_DP = 3
private const val NEEDLE_DP = 2
