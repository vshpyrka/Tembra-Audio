package audio.tembra.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.FaderTick
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.launch

/**
 * The vertical fader (feature 012, spec FR-003, FR-004, FR-027 to FR-030; research R3; contracts/mixer-ui.md "Fader"):
 * a rail with the lit track below the handle, the 44 × 28 dp handle and the scale marks of [scale], drawn in the draw
 * phase from [value]. A drag anywhere on it moves the value 1:1 in throw space from where the drag began and keeps
 * following after the finger strays sideways (`awaitEachGesture` + `drag`); every gesture ends with one [onSettled].
 * A double tap returns to the scale's unity through an [Animatable] (a jump under reduce motion); the wheel moves one
 * step per notch, the arrows one step, Page a page, Home to unity, each a settled gesture of its own. Hover shows the
 * tooltip ([description]) and the hover layer over the whole control and tints the
 * handle, keyboard focus draws the `primary` ring around it. Semantics: [description] names the control, [readout]
 * is its state, `progressBarRangeInfo` and `setProgress` work in the scale's own domain (decibels, percent).
 */
@Composable
internal fun Fader(
    value: Float,
    onValue: (Float) -> Unit,
    onSettled: () -> Unit,
    description: String,
    readout: String,
    modifier: Modifier = Modifier,
    scale: FaderScale = FaderScale.Gain,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
    /** The animation section spanning a drag: a strip's `timeline.faderDrag`, the master's `transport.masterDrag`. */
    section: PerfSection = PerfSection.TIMELINE_FADER_DRAG,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val motion = TembraTheme.motion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val current by rememberUpdatedState(value)
    val currentOnValue by rememberUpdatedState(onValue)
    val currentOnSettled by rememberUpdatedState(onSettled)
    val scope = rememberCoroutineScope()
    val returning = remember { Animatable(value) }
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = TembraTheme.typographyExtras.microLabel
    val ticks = remember(scale, tickStyle, textMeasurer) { scale.ticks.map { MeasuredTick(it, textMeasurer.measure(it.label, tickStyle)) } }
    val rail = colors.surfaceContainerHighest
    val lit = colors.primary
    val handleFill = if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val handleLine = colors.onSurface
    val tickColor = colors.onSurfaceVariant
    val ringColor = colors.primary
    val ringWidth = spacing.focusRingWidth
    val throwPosition = scale.throwOf(value)

    fun offer(next: Float): Boolean {
        val quantized = scale.quantize(next.coerceIn(scale.range))
        if (quantized == current) return false
        currentOnValue(quantized)
        return true
    }

    fun step(delta: Float) {
        offer(current + delta)
        currentOnSettled()
    }

    fun returnToUnity() {
        scope.launch {
            if (motion.reduceMotion) {
                offer(scale.unity)
            } else {
                returning.snapTo(current)
                returning.animateTo(scale.unity, tween(motion.standardDuration, easing = motion.standardEasing)) { offer(this.value) }
                offer(scale.unity)
            }
            currentOnSettled()
        }
    }

    TembraTooltip(text = description, interactionSource = source) {
        Box(
            modifier = modifier
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .focusIdentity(testTag)
                .hoverArea(hovered, TembraShapes.default)
                .defaultMinSize(minWidth = FaderDefaults.HandleWidth)
                .hoverable(source)
                .focusable(interactionSource = source)
                .pointerHoverIcon(PointerIcon.Hand)
                .drawBehind {
                    val handleHeight = FaderDefaults.HandleHeight.toPx()
                    val throwPx = (size.height - handleHeight).coerceAtLeast(0f)
                    val centreX = size.width / 2
                    val railWidth = FaderDefaults.RailWidth.toPx()
                    val railTop = handleHeight / 2
                    val handleTop = (1f - throwPosition) * throwPx
                    val handleCentre = handleTop + handleHeight / 2
                    val railRadius = CornerRadius(railWidth / 2)
                    drawRoundRect(rail, topLeft = Offset(centreX - railWidth / 2, railTop), size = Size(railWidth, throwPx), cornerRadius = railRadius)
                    drawRoundRect(lit, topLeft = Offset(centreX - railWidth / 2, handleCentre), size = Size(railWidth, railTop + throwPx - handleCentre), cornerRadius = railRadius)
                    val tickStart = centreX + railWidth / 2 + FaderDefaults.TickGap.toPx()
                    val tickLength = FaderDefaults.TickLength.toPx()
                    val labelled = size.width >= FaderDefaults.LabelledFrom.toPx()
                    for (tick in ticks) {
                        val y = railTop + (1f - tick.tick.throwPosition) * throwPx
                        val length = if (tick.tick.throwPosition == scale.throwOf(scale.unity)) tickLength * 2 else tickLength
                        drawRect(tickColor, topLeft = Offset(tickStart, y - TICK_THICKNESS_PX / 2), size = Size(length, TICK_THICKNESS_PX))
                        if (labelled) drawText(tick.layout, color = tickColor, topLeft = Offset(tickStart + tickLength * 2 + FaderDefaults.TickGap.toPx(), y - tick.layout.size.height / 2))
                    }
                    val handleRadius = CornerRadius(TembraShapes.default.toPx())
                    drawRoundRect(handleFill, topLeft = Offset(0f, handleTop), size = Size(size.width, handleHeight), cornerRadius = handleRadius)
                    val lineHeight = HANDLE_LINE_DP.dp.toPx()
                    drawRect(handleLine, topLeft = Offset(size.width * HANDLE_LINE_INSET, handleCentre - lineHeight / 2), size = Size(size.width * (1f - 2 * HANDLE_LINE_INSET), lineHeight))
                    if (focused) {
                        val ring = ringWidth.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(ring / 2, handleTop + ring / 2),
                            size = Size(size.width - ring, handleHeight - ring),
                            cornerRadius = CornerRadius(handleRadius.x - ring / 2),
                            style = Stroke(width = ring),
                        )
                    }
                }
                .pointerInput(scale) {
                    awaitEachGesture {
                        // The tap detector below consumes the down; the drag still starts from it (the `GainReadout` pattern).
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val throwPx = (size.height - FaderDefaults.HandleHeight.toPx()).coerceAtLeast(1f)
                        var startThrow = 0f
                        var startY = 0f
                        val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            change.consume()
                            startThrow = scale.throwOf(current)
                            startY = change.position.y - overSlop
                            offer(scale.valueOf((startThrow - overSlop / throwPx).coerceIn(0f, 1f)))
                        } ?: return@awaitEachGesture
                        PerfTrace.animation(section) {
                            drag(start.id) { change ->
                                change.consume()
                                offer(scale.valueOf((startThrow - (change.position.y - startY) / throwPx).coerceIn(0f, 1f)))
                            }
                        }
                        currentOnSettled()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { returnToUnity() })
                }
                .pointerInput(scale) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                            if (delta == 0.0) continue
                            step(if (delta < 0) scale.step else -scale.step)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> step(scale.step)
                        Key.DirectionDown, Key.DirectionLeft -> step(-scale.step)
                        Key.PageUp -> step(scale.page)
                        Key.PageDown -> step(-scale.page)
                        Key.MoveHome -> {
                            offer(scale.unity)
                            currentOnSettled()
                        }
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = readout
                    progressBarRangeInfo = ProgressBarRangeInfo(value, scale.range, 0)
                    setProgress { next ->
                        offer(next)
                        currentOnSettled()
                        true
                    }
                },
        )
    }
}

/** One scale mark with its label laid out once per scale and style. */
private class MeasuredTick(val tick: FaderTick, val layout: TextLayoutResult)

private const val TICK_THICKNESS_PX = 1f
private const val HANDLE_LINE_DP = 2
private const val HANDLE_LINE_INSET = 0.2f
