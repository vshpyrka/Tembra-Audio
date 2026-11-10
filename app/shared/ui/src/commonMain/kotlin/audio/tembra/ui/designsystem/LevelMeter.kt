package audio.tembra.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.LevelHold
import audio.tembra.ui.tuner.LevelSmoother
import kotlin.math.min

/**
 * A strip's two-bar level meter (feature 012, spec FR-012, FR-015; research R6; contracts/mixer-ui.md "LevelMeter"):
 * left and right bars on feature 010's scale, −60 dB (silence) to 0 dB (full scale), `meterBody` to −6 dB and
 * `meterWarning` above, a peak-hold mark that stays 1.5 s then falls ([LevelHold]), the 30 ms [LevelSmoother] between
 * frames (reduce motion drops it: the level is information), and the clip square at the top lit while the latch is set.
 * Every frame reads [reading] inside `drawBehind` through [tick], on a layer of its own, so the strip never recomposes
 * for a level (Constitution II). The semantics ("Level", "<n> dB") and the clip square's node ("Clipped, tap to reset",
 * announced once through a polite live region, a tap clearing the latch through [onResetClip]) follow [level], the
 * sheet's 10 Hz mirror.
 */
@Composable
internal fun LevelMeter(
    reading: MeterReading,
    tick: LongState,
    level: State<MeterLevel>,
    onResetClip: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    clipTestTag: String? = null,
) {
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val reduceMotion = TembraTheme.motion.reduceMotion
    val sample = remember { MeterSample() }
    val smoothers = remember { arrayOf(LevelSmoother(), LevelSmoother()) }
    val holds = remember { arrayOf(LevelHold(), LevelHold()) }
    val current = level.value
    val track = colors.surfaceContainerHighest
    val body = daw.meterBody
    val warning = daw.meterWarning
    val clip = daw.meterClip
    val mark = colors.onSurface
    Box(
        modifier = modifier
            .width(LevelMeterDefaults.Width)
            .graphicsLayer()
            .drawBehind {
                val frameNanos = tick.longValue
                reading.read(frameNanos, sample)
                val square = min(size.width, size.height)
                val barWidth = LevelMeterDefaults.BarWidth.toPx()
                val gap = LevelMeterDefaults.Gap.toPx()
                val barsTop = square + gap
                val barsHeight = (size.height - barsTop).coerceAtLeast(0f)
                val bottom = barsTop + barsHeight
                val radius = CornerRadius(barWidth / 2)
                val warningFrom = barsHeight * LevelHold.fractionOf(LevelHold.WARNING_FROM_DB)
                val markHeight = LevelMeterDefaults.MarkHeight.toPx()
                for (side in 0..1) {
                    val targetDb = LevelHold.dbOf(if (side == 0) sample.left else sample.right)
                    val levelDb = if (reduceMotion) targetDb else smoothers[side].advance(targetDb, frameNanos)
                    val holdDb = holds[side].advance(targetDb, frameNanos)
                    val x = side * (barWidth + gap)
                    drawRoundRect(track, topLeft = Offset(x, barsTop), size = Size(barWidth, barsHeight), cornerRadius = radius)
                    val fill = barsHeight * LevelHold.fractionOf(levelDb)
                    if (fill > 0f) {
                        val bodyHeight = min(fill, warningFrom)
                        drawRoundRect(body, topLeft = Offset(x, bottom - bodyHeight), size = Size(barWidth, bodyHeight), cornerRadius = radius)
                    }
                    if (fill > warningFrom) drawRect(warning, topLeft = Offset(x, bottom - fill), size = Size(barWidth, fill - warningFrom))
                    // While the sheet slides in, the bars may be shorter than the mark: nothing to place it on yet.
                    if (holdDb > LevelHold.FLOOR_DB && barsHeight > markHeight) {
                        val y = (bottom - barsHeight * LevelHold.fractionOf(holdDb)).coerceIn(barsTop, bottom - markHeight)
                        drawRect(mark, topLeft = Offset(x, y), size = Size(barWidth, markHeight))
                    }
                }
                drawRoundRect(if (sample.clipped) clip else track, topLeft = Offset.Zero, size = Size(square, square), cornerRadius = CornerRadius(TembraShapes.small.toPx()))
            }
            .semantics(mergeDescendants = true) {
                contentDescription = LevelMeterCopy.level
                stateDescription = LevelHold.announce(current.peakDb)
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        // The clip square's node: a wider target than the drawn square; a tap resets the latch while it is lit.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .requiredSize(LevelMeterDefaults.ClipTouch)
                .then(if (current.clipped) Modifier.clickable(role = Role.Button, onClick = onResetClip) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (current.clipped) LevelMeterCopy.clipped else LevelMeterCopy.clipIndicator
                    liveRegion = LiveRegionMode.Polite
                }
                .then(if (clipTestTag != null) Modifier.testTag(clipTestTag) else Modifier),
        )
    }
}
