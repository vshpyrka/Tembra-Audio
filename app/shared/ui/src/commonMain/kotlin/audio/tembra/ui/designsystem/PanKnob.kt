package audio.tembra.ui.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Pan
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The strip's pan knob (feature 012, spec FR-007, FR-008, FR-027 to FR-030; research R3; contracts/mixer-ui.md
 * "PanKnob"): a 40 dp dial whose arc runs three quarters of a turn from hard left through the centre at the top to hard
 * right, the lit part from the centre to the pointer. A vertical drag turns it 1 % per [PanKnobDefaults.DpPerPercent]
 * (upward to the right), keeps following off the dial, and a release within 5 % of the centre settles there
 * ([Pan.snapToCentre]); every gesture ends with one [onSettled]. A double tap returns to the centre; the wheel moves 1 %
 * per notch, the arrows 1 %, Page 10 %, Home to the centre, each a settled gesture. Hover tints the dial, keyboard focus
 * draws the ring. Semantics: [description] names it ("<track>, pan"), the readout ("L 35", "C", "R 20") is its state,
 * `progressBarRangeInfo` and `setProgress` work in percent.
 */
@Composable
internal fun PanKnob(
    pan: Pan,
    onPan: (Pan) -> Unit,
    onSettled: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val current by rememberUpdatedState(pan)
    val currentOnPan by rememberUpdatedState(onPan)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val dial = if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val track = colors.outlineVariant
    val lit = colors.primary
    val pointer = colors.onSurface
    val ringColor = colors.primary
    val ringWidth = spacing.focusRingWidth
    val readout = pan.readout

    /** Reports [percent] clamped unless it is the value already shown; returns what the knob now stands at. */
    fun offer(percent: Int): Pan {
        val next = Pan.clamped(percent)
        if (next != current) currentOnPan(next)
        return next
    }

    fun step(delta: Int) {
        offer(current.percent + delta)
        currentOnSettled()
    }

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .focusIdentity(testTag)
            .size(PanKnobDefaults.Size)
            .hoverable(source)
            .focusable(interactionSource = source)
            .pointerHoverIcon(PointerIcon.Hand)
            .drawBehind {
                val stroke = PanKnobDefaults.ArcStroke.toPx()
                val radius = size.minDimension / 2 - stroke
                val centre = Offset(size.width / 2, size.height / 2)
                drawCircle(dial, radius = radius - stroke, center = centre)
                val arcTopLeft = Offset(centre.x - radius, centre.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(track, START_ANGLE, SWEEP, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val fraction = (pan.percent + Pan.MAX) / (2f * Pan.MAX)
                val pointerAngle = START_ANGLE + SWEEP * fraction
                val centreAngle = START_ANGLE + SWEEP / 2
                if (pan.percent != 0) {
                    val from = minOf(centreAngle, pointerAngle)
                    drawArc(lit, from, kotlin.math.abs(pointerAngle - centreAngle), useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val radians = pointerAngle * PI / 180.0
                val inner = radius * POINTER_FROM
                val tip = Offset(centre.x + radius * cos(radians).toFloat(), centre.y + radius * sin(radians).toFloat())
                val base = Offset(centre.x + inner * cos(radians).toFloat(), centre.y + inner * sin(radians).toFloat())
                drawLine(pointer, start = base, end = tip, strokeWidth = PanKnobDefaults.PointerStroke.toPx(), cap = StrokeCap.Round)
                if (focused) drawCircle(ringColor, radius = size.minDimension / 2 - ringWidth.toPx() / 2, center = centre, style = Stroke(ringWidth.toPx()))
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dpPerPercent = PanKnobDefaults.DpPerPercent.toPx()
                    var startPercent = 0
                    var startY = 0f
                    // The value the turn reached, kept here: the composition's copy lags the last report by a frame.
                    var reached = current
                    val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        startPercent = current.percent
                        startY = change.position.y - overSlop
                        reached = offer(startPercent - (overSlop / dpPerPercent).roundToInt())
                    } ?: return@awaitEachGesture
                    PerfTrace.animation(PerfSection.TIMELINE_PAN_TURN) {
                        drag(start.id) { change ->
                            change.consume()
                            reached = offer(startPercent - ((change.position.y - startY) / dpPerPercent).roundToInt())
                        }
                    }
                    // A release near the centre settles there (spec FR-008).
                    val settledAt = reached.snapToCentre()
                    if (settledAt != reached) currentOnPan(settledAt)
                    currentOnSettled()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    offer(Pan.Centre.percent)
                    currentOnSettled()
                })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                        if (delta == 0.0) continue
                        step(if (delta < 0) STEP else -STEP)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionRight -> step(STEP)
                    Key.DirectionDown, Key.DirectionLeft -> step(-STEP)
                    Key.PageUp -> step(PAGE)
                    Key.PageDown -> step(-PAGE)
                    Key.MoveHome -> {
                        offer(Pan.Centre.percent)
                        currentOnSettled()
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = readout
                progressBarRangeInfo = ProgressBarRangeInfo(pan.percent.toFloat(), -Pan.MAX.toFloat()..Pan.MAX.toFloat(), 0)
                setProgress { next ->
                    offer(next.roundToInt())
                    currentOnSettled()
                    true
                }
            },
    )
}

/** The arc from hard left (lower left) over the top to hard right (lower right), in Compose's degrees. */
private const val START_ANGLE = 135f
private const val SWEEP = 270f
private const val POINTER_FROM = 0.45f
private const val STEP = 1
private const val PAGE = 10
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
