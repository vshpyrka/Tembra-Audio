package audio.tembra.ui.metronome.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TempoMarking
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.LinearGlow
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The tempo dial (research R8, contracts/design-system-additions.md): a square box whose ring is drawn from
 * [TempoDialGeometry] (a `surfaceContainerHigh` track over 275°, the fill in the `tempoArc` gradient over an 8 dp glow
 * copy at 50 %, eight tick marks), with the steppers inside the ring at its sides and [center] in the middle (the
 * readout by default, so the transport can put its own there).
 *
 * The gradient runs from bottom-left to top-right, as the design's SVG (drawn turned −90°) does, so the fill starts warm
 * at 12 o'clock. Keyboard focus draws a `primary` ring outside the ticks; a hovering pointer raises the empty track to
 * `surfaceContainerHighest`.
 *
 * Input: a drag that starts on the ring (not on a stepper) maps the pointer's angle onto the track (a drag that starts
 * elsewhere is left to the parent, so the column still scrolls); a wheel notch or an arrow key moves the tempo by one
 * and the page keys by ten. The dial is one focusable slider node for accessibility (range, state and the progress
 * action), tagged [testTag], between its steppers in focus order. The box is square and as large as its caller
 * allows; a cap is the caller's `Modifier.widthIn` or `sizeIn`. The composable recomposes only when its parameters
 * change: the ring reads nothing else.
 */
@Composable
internal fun TempoDial(
    tempo: Tempo,
    marking: TempoMarking,
    canStepDown: Boolean,
    canStepUp: Boolean,
    onTempo: (Tempo) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = { TempoReadout(tempo, marking) },
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "metronome.dial",
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    // The design's SVG is turned −90°, so its top-left → bottom-right gradient runs bottom-left → top-right on screen.
    val arc = TembraTheme.gradients.tempoArc.turnedCounterClockwise()
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth
    val stepperSize = spacing.tempoStepper
    val stepperInset = MetronomeDefaults.StepperInset
    // A pointer over the dial raises the empty track, as a hovered stepper raises its fill.
    val trackColor = if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val tickColor = colors.outlineVariant.copy(alpha = MetronomeDefaults.TICK_ALPHA)
    val currentTempo by rememberUpdatedState(tempo)
    val currentOnTempo by rememberUpdatedState(onTempo)
    val sweep = TempoDialGeometry.sweepFor(tempo)
    val state = MetronomeCopy.dialState(tempo, marking)

    /** Sends [next] unless it is the current tempo. */
    fun offer(next: Tempo) {
        if (next != currentTempo) currentOnTempo(next)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .hoverable(source)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The steppers straddle the ring band: a press on one belongs to the stepper, even when it moves.
                    val onStepper = isOnStepper(down.position, size, stepperSize.toPx(), stepperInset.toPx())
                    if (onStepper || !isOnRing(down.position, size)) return@awaitEachGesture
                    val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    offer(tempoAtPointer(start.position, size))
                    drag(start.id) { change ->
                        change.consume()
                        offer(tempoAtPointer(change.position, size))
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val notch = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                        if (notch == 0.0) continue
                        // Scrolling up (away from the user) is a negative delta and raises the tempo.
                        offer(currentTempo.plus(if (notch < 0) 1 else -1))
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .drawBehind {
                drawRing(sweep, trackColor, tickColor, arc.brush(size))
                if (focused) {
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension * TempoDialGeometry.FOCUS_RING_RADIUS,
                        style = Stroke(width = ring.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TempoStepper(
            icon = TembraIcon.Remove,
            enabled = canStepDown,
            contentDescription = MetronomeCopy.decreaseTempo,
            onStep = { onStep(-1) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = stepperInset),
            testTag = "$testTag.down",
        )
        // The dial's own node: composed between the steppers so keyboard focus reads "−", the dial, "+" (spec FR-029),
        // and drawn under them (zIndex) so a screen reader's touch exploration still finds each stepper on top. It covers
        // the whole box and keeps [center] clear of the steppers.
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(-1f)
                .testTag(testTag)
                .semantics {
                    // Compose has no slider role: the range and the progress action make the node a slider (a SeekBar
                    // to TalkBack, an adjustable element to VoiceOver), as Material's own Slider does.
                    stateDescription = state
                    progressBarRangeInfo = ProgressBarRangeInfo(tempo.bpm.toFloat(), Tempo.MIN.toFloat()..Tempo.MAX.toFloat(), steps = TEMPO_SLIDER_STEPS)
                    setProgress { value ->
                        offer(Tempo.clamped(value.roundToInt()))
                        true
                    }
                }
                .onKeyEvent { event -> handleKey(event, currentTempo)?.let(::offer) != null }
                .focusable(interactionSource = source)
                .padding(horizontal = stepperSize + stepperInset * 2),
            contentAlignment = Alignment.Center,
        ) {
            center()
        }
        TempoStepper(
            icon = TembraIcon.Add,
            enabled = canStepUp,
            contentDescription = MetronomeCopy.increaseTempo,
            onStep = { onStep(1) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = stepperInset),
            testTag = "$testTag.up",
        )
    }
}

/** The dial and the tempo row as a slider: one step per tempo between the ends. */
internal const val TEMPO_SLIDER_STEPS = Tempo.MAX - Tempo.MIN - 1

/** The tempo a focused dial moves to for [event], or null when the key is not the dial's. */
internal fun handleKey(event: KeyEvent, tempo: Tempo): Tempo? {
    if (event.type != KeyEventType.KeyDown) return null
    val delta = when (event.key) {
        Key.DirectionUp, Key.DirectionRight -> 1
        Key.DirectionDown, Key.DirectionLeft -> -1
        Key.PageUp -> MetronomeDefaults.PAGE_STEP
        Key.PageDown -> -MetronomeDefaults.PAGE_STEP
        else -> return null
    }
    return tempo.plus(delta)
}

/** True when [position] lies on one of the steppers, which sit [inset] inside the left and right edges, vertically centred. */
private fun isOnStepper(position: Offset, size: IntSize, stepper: Float, inset: Float): Boolean {
    val top = (size.height - stepper) / 2
    if (position.y < top || position.y > top + stepper) return false
    val onLeft = position.x >= inset && position.x <= inset + stepper
    val onRight = position.x <= size.width - inset && position.x >= size.width - inset - stepper
    return onLeft || onRight
}

/** The gradient turned 90° counter-clockwise inside its box: a fraction (x, y) moves to (y, 1 − x). */
private fun LinearGlow.turnedCounterClockwise(): LinearGlow =
    copy(start = Offset(start.y, 1f - start.x), end = Offset(end.y, 1f - end.x))

/** True when [position] lies within the ring band, so a drag there turns the dial. */
private fun isOnRing(position: Offset, size: IntSize): Boolean {
    val side = minOf(size.width, size.height).toFloat()
    val distance = hypot(position.x - size.width / 2f, position.y - size.height / 2f) / side
    return abs(distance - TempoDialGeometry.RING_RADIUS) <= TempoDialGeometry.RING_BAND
}

private fun tempoAtPointer(position: Offset, size: IntSize): Tempo =
    TempoDialGeometry.tempoAt(TempoDialGeometry.angleFromTop(position.x - size.width / 2f, position.y - size.height / 2f))

/** The track, the glow and the fill up to [sweep] degrees, then the tick marks. */
private fun DrawScope.drawRing(sweep: Float, track: Color, tick: Color, fill: Brush) {
    val side = size.minDimension
    val radius = side * TempoDialGeometry.RING_RADIUS
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val stroke = MetronomeDefaults.RingStroke.toPx()
    drawArc(
        color = track,
        startAngle = TempoDialGeometry.START_ANGLE,
        sweepAngle = TempoDialGeometry.SWEEP,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    if (sweep > 0f) {
        drawArc(
            brush = fill,
            startAngle = TempoDialGeometry.START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            alpha = MetronomeDefaults.RING_GLOW_ALPHA,
            style = Stroke(width = MetronomeDefaults.RingGlowStroke.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            brush = fill,
            startAngle = TempoDialGeometry.START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
    val tickWidth = MetronomeDefaults.TickStroke.toPx()
    for (angle in TempoDialGeometry.tickAngles) {
        val radians = angle * PI.toFloat() / 180f
        val direction = Offset(sin(radians), -cos(radians))
        drawLine(
            color = tick,
            start = center + direction * (side * TempoDialGeometry.TICK_INNER),
            end = center + direction * (side * TempoDialGeometry.TICK_OUTER),
            strokeWidth = tickWidth,
        )
    }
}
