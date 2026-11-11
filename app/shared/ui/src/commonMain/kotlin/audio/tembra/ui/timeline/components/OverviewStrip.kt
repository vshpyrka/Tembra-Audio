package audio.tembra.ui.timeline.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The overview strip above the transport bar (spec FR-015, D1): the whole project at the fit scale of the strip's own
 * width (the project, and never fewer than 32 bars), each track a thin row of clip blocks in its tint (at least 2 dp,
 * stacked; rows past the height are cut), the 1 dp `daw.playhead` line on whole pixels, and a window over the span the
 * lanes show: `daw.gridOverlay` at 24 % with a 1 dp `primary` outline and two 8 dp edge handles, never drawn narrower
 * than 24 dp.
 *
 * The strip keeps clear of the window's sides (the owner's verdict on D1, T151): each side is inset by [margin] (the
 * 16 dp page margin) plus whatever part of [gestureInsets] (the system's back-gesture zones by default; zero wherever
 * the platform reports none, as with three-button navigation) still reaches the strip past that margin and past what
 * its ancestors consumed (T153). A navigation rail, the iPhone Duo's end column and the Timeline's header column consume
 * the zone they cover, so beside them the strip keeps the margin alone; on a plain phone window it keeps the larger of
 * the two. A handle at either end of the project is then grabbed by the strip, not taken by a back swipe. The inset is
 * read in layout, not composition (a change of the zones relays the strip out without recomposing it), and lies outside
 * the tagged node: its bounds, its semantics and its pointer input cover the strip alone, and the beat-to-pixel
 * arithmetic runs on that inset width. Tests pass a zero [gestureInsets] and [margin] for a strip as wide as its host.
 *
 * A drag anywhere on the strip moves the window
 * relative to the press: sideways scrolls ([onScrollTo], the window's centre moves by the finger's offset), up zooms in
 * and down zooms out about the window's centre ([onZoomTo], the span doubling per 60 dp, held between the viewport's
 * closest and widest levels), each drag locked to its first direction (a mostly sideways one only scrolls, a mostly vertical one only zooms); a tap
 * outside the window centres it there; a double tap animates to the fit of the project, then calls [onFit]. While [playing] the line
 * reads [telemetry] once per frame in draw, on its own layer so the blocks are not redrawn; stopped it sits at
 * [positionBeats]. Nothing is allocated per frame. Tagged `timeline.overview` ("Overview", "bars 6 to 15 in view"),
 * the window `timeline.overview.window`. [height] is [OverviewStripDefaults.CompactHeight] on compact windows and
 * [OverviewStripDefaults.Height] otherwise. The Timeline composes it only while the device shows it (FR-015; T151).
 */
@Composable
internal fun OverviewStrip(
    viewport: TimelineViewport,
    tracks: ImmutableList<TrackUi>,
    playing: Boolean,
    positionBeats: Beats,
    telemetry: TransportTelemetry,
    onScrollTo: (Beats) -> Unit,
    onZoomTo: (startBeats: Beats, endBeats: Beats) -> Unit,
    onFit: () -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = OverviewStripDefaults.CompactHeight,
    margin: Dp = TembraTheme.spacing.pageMargin,
    gestureInsets: WindowInsets = WindowInsets.systemGestures,
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val density = LocalDensity.current
    val style = remember(colors, daw, density) {
        with(density) {
            OverviewStyle(
                background = daw.surfaceLevel1,
                tints = daw.trackTints,
                unavailable = daw.unavailableClip,
                windowFill = daw.gridOverlay.copy(alpha = WINDOW_FILL_ALPHA),
                accent = colors.primary,
                grip = colors.onPrimary,
                playhead = daw.playhead,
                outline = Stroke(width = OUTLINE.toPx()),
                rowPad = ROW_PAD.toPx(),
                minRow = MIN_ROW.toPx(),
                rowGap = ROW_GAP.toPx(),
                handle = HANDLE.toPx(),
                minWindow = MIN_WINDOW.toPx(),
                corner = CornerRadius(CORNER.toPx()),
                // Whole pixels, so the line is crisp at every density (at 1× a 1 px line centred on a pixel edge would blur
                // into two half-tone columns).
                playheadWidth = PLAYHEAD.toPx().roundToInt().coerceAtLeast(1).toFloat(),
            )
        }
    }
    val tick = rememberFrameTick(active = playing)
    val snapshot = remember { TransportSnapshot() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnScrollTo by rememberUpdatedState(onScrollTo)
    val currentOnZoomTo by rememberUpdatedState(onZoomTo)
    val currentOnFit by rememberUpdatedState(onFit)
    val described = barsInView(viewport)
    val scope = rememberCoroutineScope()
    val fitJob = remember { FitJob() }
    val cancelFit = remember(fitJob) { { fitJob.job?.cancel(); Unit } }
    val animateToFit = remember(fitJob, scope) {
        {
            fitJob.job?.cancel()
            fitJob.job = scope.launch { animateFit(currentViewport, currentOnZoomTo, currentOnFit) }
            Unit
        }
    }
    // The zones' reach past the margin; windowInsetsPadding then takes off what the ancestors consumed on that side.
    val gestureReach = remember(gestureInsets, margin) {
        gestureInsets.only(WindowInsetsSides.Horizontal).exclude(WindowInsets(left = margin, right = margin))
    }
    Layout(
        content = {
            Canvas(Modifier) {
                drawRect(style.background)
                drawBlocks(tracks, viewport.overviewSpanBeats(), style)
                drawWindow(viewport, style)
            }
            // The window's node: what tests and the layout inspector see of the drawn window.
            Box(Modifier.testTag(TimelineTestTags.OVERVIEW_WINDOW))
            // Its own layer: the per-frame redraw of the line leaves the blocks' recording alone.
            Canvas(Modifier.graphicsLayer {}) {
                val beats = if (playing) {
                    telemetry.read(tick.longValue, snapshot)
                    snapshot.positionBeats
                } else {
                    positionBeats.value
                }
                val x = (beats * size.width / viewport.overviewSpanBeats()).toFloat()
                if (x >= 0f && x <= size.width) {
                    val left = (x - style.playheadWidth / 2).roundToInt().toFloat()
                    drawRect(style.playhead, topLeft = Offset(left, 0f), size = Size(style.playheadWidth, size.height))
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            // Before the tag: the inset is margin, and the node, its semantics and its gestures are the strip alone.
            // The gesture zone's unconsumed remainder, then the page margin always: max(margin, zone - consumed) a side.
            .windowInsetsPadding(gestureReach)
            .padding(horizontal = margin)
            .height(height)
            .testTag(TimelineTestTags.OVERVIEW)
            .semantics {
                contentDescription = TimelineCopy.overview
                stateDescription = described
            }
            .pointerInput(Unit) {
                detectOverviewGestures(
                    viewport = { currentViewport },
                    onScrollTo = { currentOnScrollTo(it) },
                    onZoomTo = { start, end -> currentOnZoomTo(start, end) },
                    animateToFit = animateToFit,
                    cancelFit = cancelFit,
                )
            },
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val stripHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val full = Constraints.fixed(width, stripHeight)
        val windowWidth = viewport.windowWidthOn(width.toFloat(), style.minWindow).roundToInt()
        val windowLeft = viewport.windowLeftOn(width.toFloat(), style.minWindow).roundToInt()
        val blocks = measurables[0].measure(full)
        val window = measurables[1].measure(Constraints.fixed(windowWidth, stripHeight))
        val line = measurables[2].measure(full)
        layout(width, stripHeight) {
            blocks.place(0, 0)
            window.place(windowLeft, 0)
            line.place(0, 0)
        }
    }
}

/** The running double-tap fit animation, if any. */
private class FitJob {
    var job: Job? = null
}

/**
 * The double-tap fit, animated: from the lanes' current span and left edge to the fit span at its rest position (the
 * lead-in before the project's start), the span geometrically and the left edge linearly along an ease-out curve, then
 * [onFit] so the final state is exactly the fit and is saved. Already there: [onFit] alone. Cancelled by a new press.
 */
private suspend fun animateFit(from: TimelineViewport, onZoomTo: (Beats, Beats) -> Unit, onFit: () -> Unit) {
    if (from.widthPx <= 0.0) {
        onFit()
        return
    }
    val s0 = from.visibleBeats
    val a0 = from.scrollBeats.value
    val s1 = from.widthPx / (from.fitPixelsPerBeat * from.densityScale)
    val a1 = if (from.leadInPx > 0.0 && from.leadInPx < from.widthPx) -from.leadInPx * s1 / from.widthPx else 0.0
    if (s0 <= 0.0 || s1 <= 0.0 || (abs(s1 - s0) <= FIT_TOLERANCE * s1 && abs(a1 - a0) <= FIT_TOLERANCE * s1)) {
        onFit()
        return
    }
    val ratio = s1 / s0
    PerfTrace.animation(PerfSection.TIMELINE_ZOOM) {
        Animatable(0f).animateTo(1f, tween(FIT_MILLIS, easing = FastOutSlowInEasing)) {
            val t = value.toDouble()
            val span = s0 * ratio.pow(t)
            val start = a0 + (a1 - a0) * t
            onZoomTo(Beats(start), Beats(start + span))
        }
    }
    onFit()
}

/** The strip's two heights (spec FR-015, D1). */
internal object OverviewStripDefaults {
    /** On compact windows. */
    val CompactHeight: Dp = 24.dp

    /** On medium and expanded windows. */
    val Height: Dp = 28.dp
}

/** Everything the draw lambdas need, resolved once per theme and density so a frame allocates nothing. */
private class OverviewStyle(
    val background: Color,
    val tints: List<Color>,
    val unavailable: Color,
    val windowFill: Color,
    val accent: Color,
    val grip: Color,
    val playhead: Color,
    val outline: Stroke,
    val rowPad: Float,
    val minRow: Float,
    val rowGap: Float,
    val handle: Float,
    val minWindow: Float,
    val corner: CornerRadius,
    val playheadWidth: Float,
)

/** Beats across the strip: the project, and never fewer than 32 bars (the widest zoom of spec FR-015). */
private fun TimelineViewport.overviewSpanBeats(): Double = max(projectLengthBeats.value, TimelineViewport.MIN_FIT_BARS * beatsPerBar)

/** The drawn window's width on a strip [stripWidth] pixels wide: the visible span, at least [minWidth], at most the strip; zero unmeasured. */
private fun TimelineViewport.windowWidthOn(stripWidth: Float, minWidth: Float): Float {
    if (widthPx <= 0.0 || stripWidth <= 0f) return 0f
    val actual = (visibleBeats / overviewSpanBeats() * stripWidth).toFloat()
    return actual.coerceAtLeast(min(minWidth, stripWidth)).coerceAtMost(stripWidth)
}

/** The drawn window's left edge: centred on the visible span, held inside the strip. */
private fun TimelineViewport.windowLeftOn(stripWidth: Float, minWidth: Float): Float {
    val width = windowWidthOn(stripWidth, minWidth)
    if (width <= 0f) return 0f
    val centre = ((scrollBeats.value + visibleBeats / 2) / overviewSpanBeats() * stripWidth).toFloat()
    return (centre - width / 2).coerceIn(0f, stripWidth - width)
}

/** One thin row per track, its clips as blocks in the track's tint (dimmed when inaudible, grey when the audio is missing). */
private fun DrawScope.drawBlocks(tracks: ImmutableList<TrackUi>, spanBeats: Double, style: OverviewStyle) {
    val rows = tracks.size
    if (rows == 0 || spanBeats <= 0.0 || style.tints.isEmpty()) return
    val scale = size.width / spanBeats
    val pitch = max(style.minRow, (size.height - 2 * style.rowPad) / rows)
    val block = if (pitch > 2 * style.rowGap + style.minRow) pitch - style.rowGap else pitch
    for (row in 0 until rows) {
        val top = style.rowPad + row * pitch
        if (top + block > size.height - style.rowPad) break
        val track = tracks[row]
        val tint = style.tints[track.colorIndex.mod(style.tints.size)]
        val trackTint = if (track.audible) tint else tint.copy(alpha = INAUDIBLE_ALPHA)
        val clips = track.clips
        for (index in 0 until clips.size) {
            val clip = clips[index]
            val x0 = (clip.startBeats.value * scale).toFloat()
            if (x0 >= size.width) continue
            val x1 = max(x0 + 1f, (clip.endBeats.value * scale).toFloat()).coerceAtMost(size.width)
            drawRect(if (clip.available) trackTint else style.unavailable, topLeft = Offset(x0, top), size = Size(x1 - x0, block))
        }
    }
}

/** The visible span: the translucent fill, the outline and the two handles with a grip line each. */
private fun DrawScope.drawWindow(viewport: TimelineViewport, style: OverviewStyle) {
    val width = viewport.windowWidthOn(size.width, style.minWindow)
    if (width <= 0f) return
    val left = viewport.windowLeftOn(size.width, style.minWindow)
    drawRoundRect(style.windowFill, topLeft = Offset(left, 0f), size = Size(width, size.height), cornerRadius = style.corner)
    val stroke = style.outline.width
    drawRoundRect(style.accent, topLeft = Offset(left + stroke / 2, stroke / 2), size = Size(width - stroke, size.height - stroke), cornerRadius = style.corner, style = style.outline)
    val handle = min(style.handle, width / 2)
    val gripTop = size.height * (1f - GRIP_FRACTION) / 2
    val gripBottom = size.height - gripTop
    for (edge in 0..1) {
        val x = if (edge == 0) left else left + width - handle
        drawRoundRect(style.accent, topLeft = Offset(x, 0f), size = Size(handle, size.height), cornerRadius = style.corner)
        drawLine(style.grip, Offset(x + handle / 2, gripTop), Offset(x + handle / 2, gripBottom), strokeWidth = stroke)
    }
}

/** "bars 6 to 15 in view": the bars the lanes show, partly shown ones included. */
private fun barsInView(viewport: TimelineViewport): String {
    val perBar = viewport.beatsPerBar.coerceAtLeast(1).toDouble()
    val first = floor(viewport.scrollBeats.coerceAtLeastZero().value / perBar).toInt() + 1
    val last = ceil(viewport.endBeats.value / perBar - BAR_EPSILON).toInt().coerceAtLeast(first)
    return TimelineCopy.barsInView(first, last)
}

/**
 * One gesture's arithmetic in beats, fixed at the press: the window's centre and span at the press are the origin, and
 * every move is computed from the total offset since the press, so nothing drifts while the viewport follows.
 */
private class OverviewDrag(port: TimelineViewport, private val stripWidth: Float, downX: Float, downY: Float, private val zoomPx: Float) {
    /** Where the offsets are measured from: the press, moved by the slop once the drag starts (see [startAt]). */
    private var originX = downX
    private var originY = downY
    private val span = port.overviewSpanBeats()
    private val leadInPx = port.leadInPx
    private val widthPx = port.widthPx
    private val beatsPerPx = span / stripWidth
    private val startSpan = port.visibleBeats
    private val startCentre = port.scrollBeats.value + startSpan / 2
    private val narrowest = port.widthPx / (port.closestPixelsPerBeat * port.densityScale)
    private val widest = port.widthPx / (port.fitPixelsPerBeat * port.densityScale)

    /** The scroll that centres the window on [x], held inside the project. */
    fun centredAt(x: Float): Beats {
        val earliest = earliestFor(startSpan)
        return Beats(clamp(x.coerceIn(0f, stripWidth) * beatsPerPx - startSpan / 2, earliest, max(earliest, span - startSpan)))
    }

    /** The earliest scroll at a zoom showing [visible] beats: the window held at the strip's start still leaves the lead-in showing in the lanes. */
    private fun earliestFor(visible: Double): Double = if (leadInPx > 0.0 && leadInPx < widthPx) -leadInPx * visible / widthPx else 0.0

    /** True once the drag is locked to the vertical axis (zoom); false while undecided or locked horizontal (scroll). */
    private var vertical = false

    /**
     * The drag has started with the finger [moved] from the press: the axis is locked once, to the larger offset
     * (horizontal when equal), and the origin goes [slop] along that axis only, so the first frame does not leap.
     */
    fun lockAxis(downX: Float, downY: Float, moved: Offset, slop: Float) {
        vertical = abs(moved.x) < abs(moved.y)
        if (vertical) originY = downY + sign(moved.y) * slop else originX = downX + sign(moved.x) * slop
    }

    /**
     * The window for a finger at ([x], [y]) on the locked axis. Horizontal: the span stays as it was and the centre moves
     * by the horizontal offset ([onScrollTo]). Vertical: the centre stays and the span doubles per [zoomPx] of downward
     * travel (halves per [zoomPx] upward), held between the closest and widest levels ([onZoomTo]). Both are held inside
     * the project and the lead-in.
     */
    fun moveTo(x: Float, y: Float, onScrollTo: (Beats) -> Unit, onZoomTo: (Beats, Beats) -> Unit) {
        if (vertical) {
            val newSpan = clamp(startSpan * 2.0.pow(((y - originY) / zoomPx).toDouble()), narrowest, widest)
            val earliest = earliestFor(newSpan)
            val start = clamp(startCentre - newSpan / 2, earliest, max(earliest, span - newSpan))
            onZoomTo(Beats(start), Beats(start + newSpan))
        } else {
            val earliest = earliestFor(startSpan)
            val centre = startCentre + (x - originX) * beatsPerPx
            onScrollTo(Beats(clamp(centre - startSpan / 2, earliest, max(earliest, span - startSpan))))
        }
    }

    /** [value] within [low]..[high]; [low] when the range is empty (a project shorter than the closest span). */
    private fun clamp(value: Double, low: Double, high: Double): Double = if (high < low) low else value.coerceIn(low, high)
}

/**
 * The strip's pointer input, the same wherever the press lands: a move past the touch slop (in either axis) starts a drag
 * locked to its first direction, inside `PerfTrace.animation(TIMELINE_SCROLL)` (mostly horizontal at the slop: only
 * scrolls, the window's centre following the horizontal offset) or `TIMELINE_ZOOM` (mostly vertical: only zooms about
 * the window's centre, up zooms in, 2x per 60 dp); the finger may leave the strip. A release before the slop outside the
 * window centres it there; a second such tap close to the first calls [animateToFit]. A press calls [cancelFit] first.
 */
private suspend fun PointerInputScope.detectOverviewGestures(
    viewport: () -> TimelineViewport,
    onScrollTo: (Beats) -> Unit,
    onZoomTo: (Beats, Beats) -> Unit,
    animateToFit: () -> Unit,
    cancelFit: () -> Unit,
) {
    val minWindow = MIN_WINDOW.toPx()
    val zoomPx = ZOOM_DISTANCE.toPx()
    val tapReach = DOUBLE_TAP_REACH.toPx()
    var lastTapUp = -1L
    var lastTapX = 0f
    awaitEachGesture {
        val down = awaitFirstDown()
        cancelFit()
        val port = viewport()
        val stripWidth = size.width.toFloat()
        if (port.widthPx <= 0.0 || stripWidth <= 0f) return@awaitEachGesture
        val windowLeft = port.windowLeftOn(stripWidth, minWindow)
        val windowWidth = port.windowWidthOn(stripWidth, minWindow)
        val drag = OverviewDrag(port, stripWidth, down.position.x, down.position.y, zoomPx)
        val elapsed = down.uptimeMillis - lastTapUp
        val secondTap = lastTapUp >= 0 &&
            elapsed >= viewConfiguration.doubleTapMinTimeMillis && elapsed <= viewConfiguration.doubleTapTimeoutMillis &&
            abs(down.position.x - lastTapX) <= tapReach
        lastTapUp = -1L
        down.consume()
        val slop = viewConfiguration.touchSlop
        var change = down
        while ((change.position - down.position).getDistance() <= slop) {
            change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) {
                if (secondTap) {
                    animateToFit()
                } else {
                    val x = change.position.x
                    if (x < windowLeft || x > windowLeft + windowWidth) onScrollTo(drag.centredAt(x))
                    lastTapUp = change.uptimeMillis
                    lastTapX = down.position.x
                }
                change.consume()
                return@awaitEachGesture
            }
        }
        val moved = change.position - down.position
        drag.lockAxis(down.position.x, down.position.y, moved, slop)
        val section = if (abs(moved.y) > abs(moved.x)) PerfSection.TIMELINE_ZOOM else PerfSection.TIMELINE_SCROLL
        PerfTrace.animation(section) {
            while (change.pressed) {
                drag.moveTo(change.position.x, change.position.y, onScrollTo, onZoomTo)
                change.consume()
                change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            }
        }
    }
}

private const val WINDOW_FILL_ALPHA = 0.24f
private const val INAUDIBLE_ALPHA = 0.4f
/** The grip line spans this share of the handle's height. */
private const val GRIP_FRACTION = 0.4f
/** "Bar 16" ends at beat 64.0 exactly: rounding noise there must not name bar 17. */
private const val BAR_EPSILON = 1e-6
private val OUTLINE = 1.dp
private val ROW_PAD = 3.dp
private val MIN_ROW = 2.dp
private val ROW_GAP = 1.dp
private val HANDLE = 8.dp
/** The double-tap fit's duration. */
private const val FIT_MILLIS = 300
/** How close, as a share of the fit span, the view must be to the fit for the double tap to skip the animation. */
private const val FIT_TOLERANCE = 1e-3
/** Vertical finger travel that doubles (down) or halves (up) the visible span. */
private val ZOOM_DISTANCE = 60.dp
/** How far apart two taps may land and still make a double tap. */
private val DOUBLE_TAP_REACH = 48.dp
private val MIN_WINDOW = 24.dp
private val CORNER = 4.dp
private val PLAYHEAD = 1.dp
