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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ClipDragUi
import audio.tembra.ui.timeline.ClipUi
import audio.tembra.ui.timeline.Selection
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One track's clips (spec FR-016, FR-033; contracts/timeline-screen.md): a single `Canvas` draws the faint bar lines
 * and every clip in view through [drawClip]; over it, one transparent node per visible clip carries the clip's
 * semantics ("drums, track 1, bar 1 beat 1, 31 seconds", `Role.Button`, selected) and its gestures: a tap selects,
 * a long press (touch) or a press past the slop (pointer) lifts the clip and the drag moves it in beats and rows
 * until release, a secondary click opens the [ClipMenu]. A tap on empty lane clears the selection; a plain drag
 * scrolls, a pinch zooms and a wheel does either ([viewportGestures]). While a [drag] is on, the lane it came from
 * draws the clip ghosted and the lane it hovers draws the lifted copy and highlights itself; near its edges the view
 * auto-scrolls. Tagged `timeline.lane.<index>`; clips `timeline.clip.first` then `timeline.clip.<n>` in project order.
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
    val dropTarget = drag?.takeIf { it.toTrack == track.id }
    val background = if (dropTarget != null) daw.surfaceLevel1 else daw.canvasLevel0
    val selectedClipId = (selection as? Selection.ClipSelected)?.takeIf { it.trackId == track.id }?.clipId
    val heightPx = with(density) { height.toPx() }
    val rowPitchPx = with(density) { rowPitch.toPx() }
    val edgePx = with(density) { EDGE_DP.dp.toPx() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnScrollBy by rememberUpdatedState(onScrollBy)
    var menuClip by remember { mutableStateOf<ClipId?>(null) }
    var hoveredClip by remember { mutableStateOf<ClipId?>(null) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(TimelineTestTags.lane(index))
            // On the lane itself, the clips' ancestor: a drag that starts on a clip still scrolls (clarification 3).
            .pointerInput(onClearSelection) { detectTapGestures(onTap = { onClearSelection() }) }
            .viewportGestures(dragScrolls = true, onZoomAround = onZoomAround, onScrollBy = onScrollBy),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(background)
            val beatsPerBar = viewport.beatsPerBar.toDouble()
            var bar = floor(viewport.scrollBeats.value / beatsPerBar) * beatsPerBar
            while (true) {
                val x = viewport.xOf(Beats(bar)).toFloat()
                if (x > size.width) break
                if (x >= 0f) drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                bar += beatsPerBar
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
                onDismiss = { menuClip = null },
                onSplit = {
                    menuClip = null
                    onSplit()
                },
                onDelete = {
                    menuClip = null
                    onDeleteClip(clip.id)
                },
                testTag = "${TimelineTestTags.clip(firstClipIndex + clipIndex)}.menu",
                modifier = Modifier.offset { IntOffset(left.roundToInt(), 0) },
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
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onSelectClip(clip.id) }
                        .pointerInput(clip.id, rowPitchPx) {
                            awaitEachGesture {
                                var event = awaitPointerEvent()
                                while (event.changes.none { it.changedToDown() }) event = awaitPointerEvent()
                                val down = event.changes.first { it.changedToDown() }
                                if (event.buttons.isSecondaryPressed) {
                                    down.consume()
                                    onSelectClip(clip.id)
                                    menuClip = clip.id
                                    return@awaitEachGesture
                                }
                                // Pointer: past the slop; touch: after the long press (a plain drag is the lane's scroll).
                                val start = if (down.type == PointerType.Mouse) {
                                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                } else {
                                    awaitLongPressOrCancellation(down.id)
                                } ?: return@awaitEachGesture
                                onClipDragStart(clip.id)
                                var total = Offset.Zero
                                val completed = drag(start.id) { change ->
                                    total += change.positionChange()
                                    change.consume()
                                    val port = currentViewport
                                    onClipDrag(clip.id, total.x / port.devicePixelsPerBeat, (total.y / rowPitchPx).roundToInt())
                                    // Auto-scroll when the finger nears the lane's edges.
                                    val laneX = left.toFloat() + change.position.x
                                    if (laneX < edgePx) currentOnScrollBy(-EDGE_SCROLL_PX) else if (laneX > port.widthPx - edgePx) currentOnScrollBy(EDGE_SCROLL_PX)
                                }
                                if (completed) onClipDragEnd() else onClipDragCancel()
                            }
                        },
                )
            }
        }
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

internal const val UNAVAILABLE = "audio unavailable"
internal const val LOADING_WAVEFORM = "Loading waveform"
private const val OUTLINE_WIDTH_DP = 2
private const val GHOST_ALPHA = 0.35f
private const val EDGE_DP = 24
private const val EDGE_SCROLL_PX = 12.0
