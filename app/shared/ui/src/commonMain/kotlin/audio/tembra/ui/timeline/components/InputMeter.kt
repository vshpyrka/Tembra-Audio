package audio.tembra.ui.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.perf.traceFirstDraw
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.LevelHold
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.tuner.LevelSmoother
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The armed track's input level (feature 010, spec FR-005, FR-006; contracts/timeline-recording-ui.md "InputMeter"):
 * a bar from −60 dB to 0 dB in `meterBody` up to −6 dB and `meterWarning` above, the peak-hold mark of [LevelHold],
 * and a `meterClip` square at the right end that lights while the engine's clip latch is set and resets on a tap.
 * Nothing here is screen state: every frame reads [telemetry] inside `drawBehind` through the frame tick, on a layer
 * of its own, so the header never recomposes (the path of the playhead, Constitution II); reduce motion only drops the
 * smoothing, the level being information. The semantics ("Input level", "<n> dB") and the clip square's node ("Clipped,
 * tap to reset", announced once through a polite live region when the latch lights) follow a 10 Hz mirror. The first
 * draw is the `TIMELINE_ARM` section's end. Composes nothing while not [armed].
 */
@Composable
internal fun InputMeter(
    telemetry: InputTelemetry,
    armed: Boolean,
    onResetClip: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = TembraTheme.spacing.meterHeightCompact,
    testTag: String? = null,
    clipTestTag: String? = null,
) {
    if (!armed) return
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val reduceMotion = TembraTheme.motion.reduceMotion
    val tick = rememberFrameTick(active = true)
    val snapshot = remember { CaptureSnapshot() }
    val smoother = remember { LevelSmoother() }
    val hold = remember { LevelHold() }
    var announcedDb by remember { mutableIntStateOf(LevelHold.FLOOR_DB.toInt()) }
    var clipped by remember { mutableStateOf(false) }
    // The 10 Hz mirror for the semantics only: the announced level and the latch (the drawn values never pass through state).
    LaunchedEffect(telemetry) {
        val mirror = CaptureSnapshot()
        while (true) {
            val frameNanos = withFrameNanos { it }
            telemetry.read(frameNanos, mirror)
            announcedDb = LevelHold.dbOf(max(mirror.peak[0], mirror.peak[1])).roundToInt()
            clipped = mirror.clipped
            delay(MIRROR_MILLIS)
        }
    }
    val track = colors.surfaceContainerHighest
    val body = daw.meterBody
    val warning = daw.meterWarning
    val clip = daw.meterClip
    val mark = colors.onSurface
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer()
            .traceFirstDraw(PerfSection.TIMELINE_ARM)
            .drawBehind {
                val frameNanos = tick.longValue
                telemetry.read(frameNanos, snapshot)
                val targetDb = LevelHold.dbOf(max(snapshot.peak[0], snapshot.peak[1]))
                val levelDb = if (reduceMotion) targetDb else smoother.advance(targetDb, frameNanos)
                val holdDb = hold.advance(targetDb, frameNanos)
                val square = size.height
                val trackWidth = max(0f, size.width - square - CLIP_GAP_DP.dp.toPx())
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, size = Size(trackWidth, size.height), cornerRadius = radius)
                val fill = trackWidth * LevelHold.fractionOf(levelDb)
                val warningFrom = trackWidth * LevelHold.fractionOf(LevelHold.WARNING_FROM_DB)
                if (fill > 0f) drawRoundRect(body, size = Size(min(fill, warningFrom), size.height), cornerRadius = radius)
                if (fill > warningFrom) drawRect(warning, topLeft = Offset(warningFrom, 0f), size = Size(fill - warningFrom, size.height))
                if (holdDb > LevelHold.FLOOR_DB) {
                    val markWidth = MARK_WIDTH_DP.dp.toPx()
                    val x = (trackWidth * LevelHold.fractionOf(holdDb) - markWidth).coerceAtLeast(0f)
                    drawRect(mark, topLeft = Offset(x, 0f), size = Size(markWidth, size.height))
                }
                drawRoundRect(if (snapshot.clipped) clip else track, topLeft = Offset(size.width - square, 0f), size = Size(square, square), cornerRadius = radius)
            }
            // A merged node of its own: the header's description (its own merging node) does not swallow the meter's.
            .semantics(mergeDescendants = true) {
                contentDescription = TimelineCopy.inputLevel
                stateDescription = LevelHold.announce(announcedDb.toFloat())
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        // The clip square's node: wider and taller than the drawn square so a finger finds it; a tap resets the latch.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(CLIP_TOUCH_WIDTH_DP.dp)
                .requiredHeight(CLIP_TOUCH_HEIGHT_DP.dp)
                .then(if (clipped) Modifier.clickable(role = Role.Button, onClick = onResetClip) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (clipped) TimelineCopy.clipped else CLIP_INDICATOR
                    liveRegion = LiveRegionMode.Polite
                }
                .then(if (clipTestTag != null) Modifier.testTag(clipTestTag) else Modifier),
        )
    }
}

/** The clip square's name while the latch is off; it becomes "Clipped, tap to reset" when it lights. */
internal const val CLIP_INDICATOR = "Clip indicator"
private const val MIRROR_MILLIS = 100L
private const val CLIP_GAP_DP = 2
private const val MARK_WIDTH_DP = 2
private const val CLIP_TOUCH_WIDTH_DP = 24
private const val CLIP_TOUCH_HEIGHT_DP = 24
