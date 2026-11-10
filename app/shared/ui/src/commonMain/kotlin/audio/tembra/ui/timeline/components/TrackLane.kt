package audio.tembra.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TempoMap
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.engine.CaptureSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.session.LiveTakeUi
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.designsystem.PointerShapes
import audio.tembra.ui.designsystem.menuOffsetAt
import audio.tembra.ui.designsystem.onSecondaryClick
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ClipDragUi
import audio.tembra.ui.timeline.ClipUi
import audio.tembra.ui.timeline.Selection
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * One track's clips (spec FR-016, FR-033; contracts/timeline-screen.md): a single `Canvas` draws the faint bar lines,
 * over them the snap lines of [grid] (spec FR-018, D1: 1 px in `gridOverlay`, thinned by [snapLineStep] so they never
 * crowd closer than 4 dp; none when Off), and every clip in view through [drawClip]; over it, one transparent node per
 * visible clip carries the clip's semantics ("drums, track 1, bar 1 beat 1, 31 seconds", `Role.Button`, selected) and
 * its gestures: a tap selects, a long press (touch) or a press past the slop (pointer) lifts the clip and the drag
 * moves it in beats and rows until release, a secondary click opens the [ClipMenu]. A tap on empty lane clears the
 * selection; a plain drag scrolls, a pinch zooms and a wheel does either ([viewportGestures]). While a [drag] is on,
 * the lane it came from draws the clip ghosted and the lane it hovers draws the lifted copy and highlights itself;
 * near its edges the view auto-scrolls. Tagged `timeline.lane.<index>`; clips `timeline.clip.first` then
 * `timeline.clip.<n>` in project order.
 *
 * While a [liveTake] of this track runs (feature 010) the lane also draws the growing clip over the others through
 * [drawLiveTake]: its right edge and waveform length are read per frame from [inputTelemetry] inside the draw lambda
 * (`PerfSection.TIMELINE_TAKE_FRAME`), converted to beats by [tempoMap]; the take is rebuilt only from the 10 Hz
 * mirror. One transparent node `timeline.clip.take` ("Recording, Take 2, 00:04.10", no click) sits on it.
 */
@Composable
internal fun TrackLane(
    track: TrackUi,
    index: Int,
    firstClipIndex: Int,
    viewport: TimelineViewport,
    selection: Selection,
    signature: TimeSignature,
    height: Dp,
    onSelectClip: (ClipId) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    grid: GridSize = GridSize.Default,
    onZoomAround: (centreX: Double, factor: Double) -> Unit = { _, _ -> },
    onScrollBy: (px: Double) -> Unit = {},
    drag: ClipDragUi? = null,
    rowPitch: Dp = height,
    canSplit: Boolean = false,
    onClipDragStart: (ClipId) -> Unit = {},
    onClipDrag: (ClipId, deltaBeats: Double, deltaTracks: Int) -> Unit = { _, _, _ -> },
    onClipDragEnd: () -> Unit = {},
    onClipDragCancel: () -> Unit = {},
    onSplit: () -> Unit = {},
    onDeleteClip: (ClipId) -> Unit = {},
    tempoMap: TempoMap? = null,
    liveTake: LiveTakeUi? = null,
    inputTelemetry: InputTelemetry? = null,
    menuClip: ClipId? = null,
    onMenuClip: (ClipId?) -> Unit = {},
) {
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val captionStyle = TembraTheme.typographyExtras.microLabel
    val style = remember(daw, colors, captionStyle, density) {
        ClipStyle(
            tints = daw.trackTints,
            captionBand = daw.clipCaptionBand,
            selectionOutline = daw.selectionOutline,
            unavailable = daw.unavailableClip,
            cornerRadiusPx = with(density) { TembraShapes.clip.toPx() },
            outlineWidthPx = with(density) { OUTLINE_WIDTH_DP.dp.toPx() },
            captionStyle = captionStyle,
            captionColor = colors.onSurface,
            placeholderColor = colors.outlineVariant,
        )
    }
    val tint = style.tintOf(track.colorIndex)
    val gridColor = daw.gridOverlayFaint
    val snapColor = daw.gridOverlay
    val dropTarget = drag?.takeIf { it.toTrack == track.id }
    val background = if (dropTarget != null) daw.surfaceLevel1 else daw.canvasLevel0
    val selectedClipId = (selection as? Selection.ClipSelected)?.takeIf { it.trackId == track.id }?.clipId
    val heightPx = with(density) { height.toPx() }
    val rowPitchPx = with(density) { rowPitch.toPx() }
    val edgePx = with(density) { EDGE_DP.dp.toPx() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnScrollBy by rememberUpdatedState(onScrollBy)
    var hoveredClip by remember { mutableStateOf<ClipId?>(null) }
    val take = if (liveTake != null && liveTake.trackId == track.id && tempoMap != null && inputTelemetry != null) liveTake else null
    val takeTick = rememberFrameTick(active = take != null)
    val captureSnapshot = remember { CaptureSnapshot() }
    val takeClip = remember(take) {
        take?.let {
            ClipUi(
                id = ClipId("live-take"), assetId = AudioAssetId(""), name = it.clipName, startBeats = it.startBeats,
                lengthBeats = Beats.Zero, offsetFrames = 0, lengthFrames = it.framesCaptured, sampleRate = it.sampleRate,
                overview = it.overview, overviewStatus = OverviewStatus.Ready, available = true,
            )
        }
    }
    // Where a secondary click opened the menu (feature 011, FR-012); the overflow-less clip has no other anchor.
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // A clip that starts left of the view is drawn from a negative x: the lane keeps it to itself, so it never
            // paints over the header pinned beside it in the expanded layout (feature 011, large windows).
            .clipToBounds()
            .testTag(TimelineTestTags.lane(index))
            // On the lane itself, the clips' ancestor: a drag that starts on a clip still scrolls (clarification 3).
            .pointerInput(onClearSelection) { detectTapGestures(onTap = { onClearSelection() }) }
            .viewportGestures(dragScrolls = true, onZoomAround = onZoomAround, onScrollBy = onScrollBy),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(background)
            val beatsPerBar = viewport.beatsPerBar.toDouble()
            // Bar lines start at the project's start: the lead-in before it stays empty.
            var bar = floor(viewport.scrollBeats.coerceAtLeastZero().value / beatsPerBar) * beatsPerBar
            while (true) {
                val x = viewport.xOf(Beats(bar)).toFloat()
                if (x > size.width) break
                if (x >= 0f) drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                bar += beatsPerBar
            }
            snapLineStep(grid, signature, viewport.devicePixelsPerBeat, MIN_SNAP_SPACING_DP.dp.toPx().toDouble())?.let { step ->
                drawSnapLines(viewport, step, snapColor)
            }
            for (clip in track.clips) {
                val left = viewport.xOf(clip.startBeats).toFloat()
                val right = viewport.xOf(clip.endBeats).toFloat()
                if (right < 0f || left > size.width) continue
                val ghosted = drag != null && drag.clipId == clip.id
                if (ghosted) {
                    val paint = Paint().apply { alpha = GHOST_ALPHA }
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                    drawClip(clip, left, right, tint, selected = false, style = style, measurer = measurer)
                    drawContext.canvas.restore()
                } else {
                    drawClip(clip, left, right, tint, selected = clip.id == selectedClipId, style = style, measurer = measurer, hovered = clip.id == hoveredClip)
                }
            }
            // The growing take of a running recording, above the clips (feature 010): length from the capture telemetry per frame.
            if (take != null && takeClip != null && tempoMap != null && inputTelemetry != null) {
                PerfTrace.section(PerfSection.TIMELINE_TAKE_FRAME) {
                    inputTelemetry.read(takeTick.longValue, captureSnapshot)
                    val seconds = if (captureSnapshot.sampleRate > 0) captureSnapshot.framesCapturedSeconds else take.lengthSeconds
                    val frames = if (captureSnapshot.sampleRate > 0) captureSnapshot.framesCaptured else take.framesCaptured
                    val left = viewport.xOf(take.startBeats).toFloat()
                    val right = viewport.xOf(take.startBeats + tempoMap.secondsToBeats(seconds)).toFloat()
                    if (right >= 0f && left <= size.width) drawLiveTake(takeClip, left, right, frames, tint, style, measurer)
                }
            }
            // The lifted copy on the lane under the finger (spec FR-033): the drop position, snapped, outlined.
            if (dropTarget != null) {
                val source = track.clips.firstOrNull { it.id == dropTarget.clipId } ?: ClipUi(
                    id = dropTarget.clipId, assetId = AudioAssetId(""), name = dropTarget.name, startBeats = dropTarget.startBeats,
                    lengthBeats = dropTarget.lengthBeats, offsetFrames = 0, lengthFrames = 1, sampleRate = 1, overview = null,
                    overviewStatus = OverviewStatus.Ready, available = true,
                )
                val lifted = source.copy(startBeats = dropTarget.startBeats, lengthBeats = dropTarget.lengthBeats)
                val left = viewport.xOf(lifted.startBeats).toFloat()
                val right = viewport.xOf(lifted.endBeats).toFloat()
                if (right >= 0f && left <= size.width) drawClip(lifted, left, right, tint, selected = true, style = style, measurer = measurer)
            }
        }
        if (take != null && tempoMap != null) {
            val left = viewport.xOf(take.startBeats)
            val right = viewport.xOf(take.startBeats + tempoMap.secondsToBeats(take.lengthSeconds))
            if (viewport.widthPx > 0.0 && right >= 0.0 && left <= viewport.widthPx) {
                val description = "$RECORDING_PREFIX, ${take.clipName}, ${TimeFormat.clock(take.lengthSeconds)}"
                Box(
                    modifier = Modifier
                        .offset { IntOffset(left.roundToInt(), 0) }
                        .width(with(density) { (right - left).toFloat().toDp() })
                        .height(with(density) { heightPx.toDp() })
                        .testTag(TimelineTestTags.TAKE_CLIP)
                        .semantics { contentDescription = description },
                )
            }
        }
        track.clips.forEachIndexed { clipIndex, clip ->
            val left = viewport.xOf(clip.startBeats)
            val right = viewport.xOf(clip.endBeats)
            if (viewport.widthPx <= 0.0 || right < 0.0 || left > viewport.widthPx) return@forEachIndexed
            val description = clipDescription(clip, index, signature)
            val state = clipState(clip)
            val interaction = remember(clip.id) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            LaunchedEffect(hovered, clip.id) { if (hovered) hoveredClip = clip.id else if (hoveredClip == clip.id) hoveredClip = null }
            ClipMenu(
                expanded = menuClip == clip.id,
                canSplit = canSplit && selectedClipId == clip.id,
                onDismiss = { onMenuClip(null) },
                onSplit = {
                    onMenuClip(null)
                    onSplit()
                },
                onDelete = {
                    onMenuClip(null)
                    onDeleteClip(clip.id)
                },
                testTag = "${TimelineTestTags.clip(firstClipIndex + clipIndex)}.menu",
                modifier = Modifier.offset { IntOffset(left.roundToInt(), 0) },
                offset = menuOffset,
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { (right - left).toFloat().toDp() })
                        .height(with(density) { heightPx.toDp() })
                        .testTag(TimelineTestTags.clip(firstClipIndex + clipIndex))
                        .semantics {
                            contentDescription = description
                            selected = clip.id == selectedClipId
                            if (state != null) stateDescription = state
                        }
                        // Before the click: a right-click opens the menu at the pointer and never selects twice (feature 011).
                        .onSecondaryClick(clip.id) { position ->
                            PerfTrace.section(PerfSection.TIMELINE_CONTEXT_MENU) {
                                onSelectClip(clip.id)
                                menuOffset = density.menuOffsetAt(position, heightPx.roundToInt())
                                onMenuClip(clip.id)
                            }
                        }
                        // An open hand over a clip, a closed one while it is lifted (feature 011, spec FR-015).
                        .pointerHoverIcon(if (drag?.clipId == clip.id) PointerShapes.grabbing else PointerShapes.grab)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onSelectClip(clip.id) }
                        .pointerInput(clip.id, rowPitchPx) {
                            awaitEachGesture {
                                var event = awaitPointerEvent()
                                while (event.changes.none { it.changedToDown() }) event = awaitPointerEvent()
                                val down = event.changes.first { it.changedToDown() }
                                // Pointer: past the slop; touch: after the long press (a plain drag is the lane's scroll).
                                val start = if (down.type == PointerType.Mouse) {
                                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                } else {
                                    awaitLongPressOrCancellation(down.id)
                                } ?: return@awaitEachGesture
                                onClipDragStart(clip.id)
                                var total = Offset.Zero
                                var settled = false
                                try {
                                    val completed = drag(start.id) { change ->
                                        total += change.positionChange()
                                        change.consume()
                                        val port = currentViewport
                                        onClipDrag(clip.id, total.x / port.devicePixelsPerBeat, (total.y / rowPitchPx).roundToInt())
                                        // Auto-scroll when the finger nears the lane's edges.
                                        val laneX = left.toFloat() + change.position.x
                                        if (laneX < edgePx) currentOnScrollBy(-EDGE_SCROLL_PX) else if (laneX > port.widthPx - edgePx) currentOnScrollBy(EDGE_SCROLL_PX)
                                    }
                                    settled = true
                                    if (completed) onClipDragEnd() else onClipDragCancel()
                                } finally {
                                    // The gesture ended without a release: the lane left composition or changed form (a window
                                    // crossed a layout class, feature 011 FR-007), so the clip returns rather than staying half-moved.
                                    if (!settled) onClipDragCancel()
                                }
                            }
                        },
                )
            }
        }
    }
}

/**
 * The beats between two drawn snap lines (spec FR-018): the [grid]'s step, or, while lines that close would sit nearer
 * than [minSpacingPx], the next coarser level that is still a multiple of it (1/16, 1/8, 1/4, the bar, then 2, 4, 8…
 * bars), so the lines drawn are always snap targets and never finer than the picked value; null when the grid is off
 * or the zoom is not measured.
 */
internal fun snapLineStep(grid: GridSize, signature: TimeSignature, devicePixelsPerBeat: Double, minSpacingPx: Double): Double? {
    val picked = grid.beatsOf(signature)?.value ?: return null
    if (!(devicePixelsPerBeat > 0.0) || devicePixelsPerBeat.isInfinite()) return null
    fun fits(step: Double) = step >= picked && isMultipleOf(step, picked) && step * devicePixelsPerBeat >= minSpacingPx
    for (level in SNAP_LEVELS) {
        val step = level.beatsOf(signature)?.value ?: continue
        if (fits(step)) return step
    }
    var bars = signature.beats * 2.0
    repeat(MAX_BAR_DOUBLINGS) {
        if (fits(bars)) return bars
        bars *= 2
    }
    return null
}

/** The finest level first: the order [snapLineStep] coarsens in, up to the bar. */
private val SNAP_LEVELS = listOf(GridSize.Sixteenth, GridSize.Eighth, GridSize.Quarter, GridSize.Bar)

private fun isMultipleOf(value: Double, step: Double): Boolean {
    val ratio = value / step
    return abs(ratio - round(ratio)) < MULTIPLE_TOLERANCE
}

/** One 1 px column every [step] beats from the first at or after the left edge to the canvas's right edge, on whole pixels. */
private fun DrawScope.drawSnapLines(viewport: TimelineViewport, step: Double, color: Color) {
    var line = ceil(viewport.scrollBeats.coerceAtLeastZero().value / step - MULTIPLE_TOLERANCE)
    while (true) {
        val x = floor(viewport.xOf(Beats(line * step))).toFloat()
        if (x >= size.width) break
        if (x >= 0f) drawLine(color, Offset(x + HALF_PIXEL, 0f), Offset(x + HALF_PIXEL, size.height), strokeWidth = SNAP_STROKE_PX)
        line += 1.0
    }
}

/** "drums, track 1, bar 1 beat 1, 31 seconds", with "unavailable" appended when the audio is missing (spec FR-058). */
internal fun clipDescription(clip: ClipUi, trackIndex: Int, signature: TimeSignature): String {
    val beatsPerBar = signature.beats
    val bar = floor(clip.startBeats.value / beatsPerBar).toInt() + 1
    val beat = floor(clip.startBeats.value - (bar - 1) * beatsPerBar).toInt() + 1
    val seconds = clip.lengthSeconds.roundToInt()
    val base = "${clip.name}, track ${trackIndex + 1}, bar $bar beat $beat, $seconds seconds"
    return if (clip.available) base else "$base, $UNAVAILABLE"
}

/** The overview's progress as a state for screen readers; null once drawn. */
internal fun clipState(clip: ClipUi): String? = when (val status = clip.overviewStatus) {
    is OverviewStatus.Building -> if (clip.available) "$LOADING_WAVEFORM ${(status.completedFraction * 100).roundToInt()} %" else null
    OverviewStatus.Ready, OverviewStatus.Failed -> null
}

internal const val RECORDING_PREFIX = "Recording"
internal const val UNAVAILABLE = "audio unavailable"
internal const val LOADING_WAVEFORM = "Loading waveform"
private const val OUTLINE_WIDTH_DP = 2
private const val GHOST_ALPHA = 0.35f
private const val EDGE_DP = 24
private const val EDGE_SCROLL_PX = 12.0
private const val MIN_SNAP_SPACING_DP = 4
private const val SNAP_STROKE_PX = 1f
private const val HALF_PIXEL = 0.5f
private const val MULTIPLE_TOLERANCE = 1e-9
private const val MAX_BAR_DOUBLINGS = 32
