package audio.tembra.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import audio.tembra.core.timeline.RulerLabels
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The pinned ruler (spec FR-013, FR-014; contracts/timeline-screen.md): [leftPills] (the tempo and signature pills)
 * then one `Canvas` of the bar row above the time row, labelled from [RulerLabels] for the [viewport], with ticks one
 * level below each labelled level, the bar-row grid lines, and the playhead's rounded "bar.beat" chip, which reads
 * [telemetry] in the draw phase every frame while [playing] and draws at [positionBeats] otherwise. A tap seeks
 * ([onSeek] with the beat under the finger; the owner snaps it), a horizontal drag scrubs silently
 * ([onScrubStart], [onScrub], [onScrubEnd]; clarification 4), and a hovering pointer shows the time under it. The
 * canvas is tagged `timeline.ruler`; its `stateDescription` is the 10 Hz "bar.beat".
 */
@Composable
internal fun TimelineRuler(
    viewport: TimelineViewport,
    tempoMap: TempoMap,
    playing: Boolean,
    positionBeats: Beats,
    telemetry: TransportTelemetry,
    onSeek: (Beats) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Beats) -> Unit,
    onScrubEnd: (Beats) -> Unit,
    modifier: Modifier = Modifier,
    onZoomAround: (centreX: Double, factor: Double) -> Unit = { _, _ -> },
    onScrollBy: (px: Double) -> Unit = {},
    leftPills: @Composable () -> Unit = {},
) {
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val labelStyle: TextStyle = TembraTheme.typographyExtras.tabularParam.copy(color = colors.onSurfaceVariant)
    val chipStyle: TextStyle = TembraTheme.typographyExtras.tabularParam.copy(color = colors.onSecondaryContainer)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tick = rememberFrameTick(active = playing)
    val snapshot = remember { TransportSnapshot() }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val chipText = TimeFormat.barBeat(positionBeats, tempoMap.timeSignature)
    // Label widths measured once per style: the widest bar label ("128.4.4") and time label ("00:00.00").
    val barLabelWidth = remember(labelStyle, measurer) { measurer.measure("888.8.8", labelStyle).size.width.toDouble() }
    val timeLabelWidth = remember(labelStyle, measurer) { measurer.measure("88:88.88", labelStyle).size.width.toDouble() }
    val gapPx = with(density) { spacing.sm.toPx().toDouble() }
    val playheadColor = daw.playhead
    val chipColor = daw.playheadChip
    val gridColor = daw.gridOverlay
    val tickColor = colors.outlineVariant
    val hoverColor = colors.onSurface

    Row(
        modifier = modifier.fillMaxWidth().height(spacing.rulerHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leftPills()
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(TimelineTestTags.RULER)
                .semantics {
                    contentDescription = RULER_DESCRIPTION
                    stateDescription = chipText
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset -> currentOnSeek(currentViewport.beatsAt(offset.x.toDouble())) })
                }
                .viewportGestures(dragScrolls = false, onZoomAround = onZoomAround, onScrollBy = onScrollBy)
                .pointerInput(Unit) {
                    var last = Beats.Zero
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            last = currentViewport.beatsAt(offset.x.toDouble()).coerceAtLeastZero()
                            currentOnScrubStart()
                            currentOnScrub(last)
                        },
                        onDragEnd = { currentOnScrubEnd(last) },
                        onDragCancel = { currentOnScrubEnd(last) },
                        onHorizontalDrag = { change, _ ->
                            last = currentViewport.beatsAt(change.position.x.toDouble()).coerceAtLeastZero()
                            currentOnScrub(last)
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Hover is a pointer that moves without pressing; a pressed move is the scrub above (Constitution IV).
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Move -> if (event.changes.none { it.pressed }) hoverX = event.changes.firstOrNull()?.position?.x
                                PointerEventType.Exit -> hoverX = null
                            }
                        }
                    }
                },
        ) {
            val rowHeight = size.height / 2
            val labels = RulerLabels.compute(viewport, tempoMap, barLabelWidth, timeLabelWidth, gapPx)
            // Bar row: ticks one level below, grid lines at the labelled level, then the labels.
            for (x in labels.bars.ticks) drawLine(tickColor, Offset(x.toFloat(), rowHeight * 0.7f), Offset(x.toFloat(), rowHeight))
            for (label in labels.bars.labels) {
                val x = label.x.toFloat()
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                val layout = measurer.measure(label.text, labelStyle)
                drawText(layout, topLeft = Offset(x + TEXT_INSET, (rowHeight - layout.size.height) / 2))
            }
            // Time row.
            for (x in labels.time.ticks) drawLine(tickColor, Offset(x.toFloat(), size.height * 0.85f), Offset(x.toFloat(), size.height))
            for (label in labels.time.labels) {
                val x = label.x.toFloat()
                drawLine(tickColor, Offset(x, rowHeight), Offset(x, size.height))
                val layout = measurer.measure(label.text, labelStyle)
                drawText(layout, topLeft = Offset(x + TEXT_INSET, rowHeight + (rowHeight - layout.size.height) / 2))
            }
            // Hover time under the pointer (Constitution IV).
            hoverX?.let { x ->
                val seconds = tempoMap.beatsToSeconds(viewport.beatsAt(x.toDouble()).coerceAtLeastZero())
                val layout = measurer.measure(TimeFormat.clock(seconds), labelStyle.copy(color = hoverColor))
                drawLine(hoverColor.copy(alpha = HOVER_ALPHA), Offset(x, 0f), Offset(x, size.height))
                drawText(layout, topLeft = Offset(x + TEXT_INSET, rowHeight + (rowHeight - layout.size.height) / 2))
            }
            // The playhead chip: the audible position per frame while playing, the 10 Hz position otherwise.
            val beats = if (playing) {
                telemetry.read(tick.longValue, snapshot)
                Beats(snapshot.positionBeats)
            } else {
                positionBeats
            }
            val x = viewport.xOf(beats).toFloat()
            if (x >= -CHIP_REACH && x <= size.width + CHIP_REACH) {
                val text = if (playing) TimeFormat.barBeat(beats, tempoMap.timeSignature) else chipText
                val layout = measurer.measure(text, chipStyle)
                val chipWidth = layout.size.width + CHIP_PADDING * 2
                val chipHeight = rowHeight - CHIP_INSET * 2
                drawRoundRect(chipColor, topLeft = Offset(x - chipWidth / 2, CHIP_INSET), size = Size(chipWidth, chipHeight), cornerRadius = CornerRadius(chipHeight / 2))
                drawText(layout, topLeft = Offset(x - layout.size.width / 2, CHIP_INSET + (chipHeight - layout.size.height) / 2))
                drawLine(playheadColor, Offset(x, rowHeight), Offset(x, size.height), strokeWidth = PLAYHEAD_STROKE)
            }
        }
    }
}

internal const val RULER_DESCRIPTION = "Ruler"
private const val TEXT_INSET = 4f
private const val CHIP_PADDING = 8f
private const val CHIP_INSET = 3f
private const val CHIP_REACH = 60f
private const val PLAYHEAD_STROKE = 2f
private const val HOVER_ALPHA = 0.5f
