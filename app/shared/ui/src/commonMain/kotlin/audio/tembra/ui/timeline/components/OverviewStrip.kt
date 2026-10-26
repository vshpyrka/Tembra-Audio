package audio.tembra.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The overview strip above the transport bar (spec FR-015, D1): the whole project at the fit scale of the strip's own
 * width (the project, and never fewer than 32 bars), each track a thin row of clip blocks in its tint (at least 2 dp,
 * stacked; rows past the height are cut), the 1 dp `daw.playhead` line, and a window over the span the lanes show:
 * `daw.gridOverlay` at 24 % with a 1 dp `primary` outline and two 8 dp edge handles, never drawn narrower than 24 dp.
 *
 * Dragging inside the window scrolls ([onScrollTo] with the new left-edge beat: the window follows the finger, held
 * inside the project); dragging a handle zooms ([onZoomTo] with the new span, the opposite edge fixed, held between
 * the viewport's widest and closest levels); a tap outside centres the window on the tap. While [playing] the line
 * reads [telemetry] once per frame in draw, on its own layer so the blocks are not redrawn; stopped it sits at
 * [positionBeats]. Nothing is allocated per frame. Tagged `timeline.overview` ("Overview", "bars 6 to 15 in view"),
 * the window `timeline.overview.window`. [height] is [OverviewStripDefaults.CompactHeight] on compact windows and
 * [OverviewStripDefaults.Height] otherwise.
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
    modifier: Modifier = Modifier,
    height: Dp = OverviewStripDefaults.CompactHeight,
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
                playheadWidth = PLAYHEAD.toPx(),
            )
        }
    }
    val tick = rememberFrameTick(active = playing)
    val snapshot = remember { TransportSnapshot() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnScrollTo by rememberUpdatedState(onScrollTo)
    val currentOnZoomTo by rememberUpdatedState(onZoomTo)
    val described = barsInView(viewport)
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
                if (x >= 0f && x <= size.width) drawLine(style.playhead, Offset(x, 0f), Offset(x, size.height), strokeWidth = style.playheadWidth)
            }
        },
        modifier = modifier
            .fillMaxWidth()
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
    val first = floor(viewport.scrollBeats.value / perBar).toInt() + 1
    val last = ceil(viewport.endBeats.value / perBar - BAR_EPSILON).toInt().coerceAtLeast(first)
    return TimelineCopy.barsInView(first, last)
}

/** What a press lands on: the window's body, one of its edges (within reach of the drawn handle) or the strip outside it. */
private enum class OverviewTarget {
    Window,
    LeftEdge,
    RightEdge,
    Outside,
    ;

    companion object {
        fun at(x: Float, left: Float, width: Float, reach: Float): OverviewTarget {
            val right = left + width
            // A narrow window keeps its middle third for scrolling.
            val inward = min(reach, width / 3)
            return when {
                x >= left - reach && x <= left + inward && abs(x - left) <= abs(x - right) -> LeftEdge
                x >= right - inward && x <= right + reach -> RightEdge
                x > left && x < right -> Window
                else -> Outside
            }
        }
    }
}

/** One gesture's arithmetic in beats, fixed at the press: the edge that stays put never drifts while the viewport follows. */
private class OverviewDrag(port: TimelineViewport, private val stripWidth: Float, private val target: OverviewTarget, downX: Float) {
    private val span = port.overviewSpanBeats()
    private val beatsPerPx = span / stripWidth
    private val start = port.scrollBeats.value
    private val visible = port.visibleBeats
    private val end = start + visible
    private val narrowest = port.widthPx / (port.closestPixelsPerBeat * port.densityScale)
    private val widest = port.widthPx / (port.fitPixelsPerBeat * port.densityScale)
    private val grab = downX * beatsPerPx - when (target) {
        OverviewTarget.Window, OverviewTarget.LeftEdge -> start
        OverviewTarget.RightEdge -> end
        OverviewTarget.Outside -> 0.0
    }

    /** The scroll that centres the window on [x], held inside the project. */
    fun centredAt(x: Float): Beats = Beats(clamp(at(x) - visible / 2, 0.0, max(0.0, span - visible)))

    fun moveTo(x: Float, onScrollTo: (Beats) -> Unit, onZoomTo: (Beats, Beats) -> Unit) {
        when (target) {
            OverviewTarget.Window -> onScrollTo(Beats(clamp(at(x) - grab, 0.0, max(0.0, span - visible))))
            OverviewTarget.Outside -> onScrollTo(centredAt(x))
            OverviewTarget.RightEdge -> onZoomTo(Beats(start), Beats(clamp(at(x) - grab, start + narrowest, start + widest)))
            OverviewTarget.LeftEdge -> onZoomTo(Beats(clamp(at(x) - grab, max(0.0, end - widest), end - narrowest)), Beats(end))
        }
    }

    private fun at(x: Float): Double = x.coerceIn(0f, stripWidth) * beatsPerPx

    /** [value] within [low]..[high]; [low] when the range is empty (a project shorter than the closest span). */
    private fun clamp(value: Double, low: Double, high: Double): Double = if (high < low) low else value.coerceIn(low, high)
}

/**
 * The strip's pointer input: a press past the touch slop drags (the window, an edge, or from outside the window, which
 * then follows the finger's centre), inside `PerfTrace.animation(TIMELINE_SCROLL)` or `TIMELINE_ZOOM`; a release
 * before the slop outside the window centres it there.
 */
private suspend fun PointerInputScope.detectOverviewGestures(
    viewport: () -> TimelineViewport,
    onScrollTo: (Beats) -> Unit,
    onZoomTo: (Beats, Beats) -> Unit,
) {
    val minWindow = MIN_WINDOW.toPx()
    val reach = HANDLE_REACH.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val port = viewport()
        val stripWidth = size.width.toFloat()
        if (port.widthPx <= 0.0 || stripWidth <= 0f) return@awaitEachGesture
        val target = OverviewTarget.at(down.position.x, port.windowLeftOn(stripWidth, minWindow), port.windowWidthOn(stripWidth, minWindow), reach)
        val drag = OverviewDrag(port, stripWidth, target, down.position.x)
        down.consume()
        val slop = viewConfiguration.touchSlop
        var change = down
        while (abs(change.position.x - down.position.x) <= slop) {
            change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) {
                if (target == OverviewTarget.Outside) onScrollTo(drag.centredAt(change.position.x))
                change.consume()
                return@awaitEachGesture
            }
        }
        val section = if (target == OverviewTarget.LeftEdge || target == OverviewTarget.RightEdge) PerfSection.TIMELINE_ZOOM else PerfSection.TIMELINE_SCROLL
        PerfTrace.animation(section) {
            while (change.pressed) {
                drag.moveTo(change.position.x, onScrollTo, onZoomTo)
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
/** How far outside the drawn handle a press still takes the edge: a finger's half-width. */
private val HANDLE_REACH = 16.dp
private val MIN_WINDOW = 24.dp
private val CORNER = 4.dp
private val PLAYHEAD = 1.dp
