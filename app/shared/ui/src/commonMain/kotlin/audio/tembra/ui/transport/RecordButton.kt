package audio.tembra.ui.transport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme

/**
 * The transport's Record control (feature 010, contracts/timeline-recording-ui.md; spec FR-007, FR-009, FR-029): a
 * 44 dp [TransportButton]-shaped target drawing the design's record circle in `DawColors.record`. [RecordButtonState.Idle]
 * draws the hollow circle; [RecordButtonState.CountIn] the filled circle with a ring that pulses out once per beat (the
 * ring restarts whenever [countInBeatsLeft] changes; it is drawn at rest under reduce motion); [RecordButtonState.Recording]
 * the filled circle with a glow (none under reduce motion). [RecordButtonState.Hidden] composes nothing: the bar hides
 * Record by width and the armed track's compact header shows it instead. The accessible name is "Record", "Recording" or
 * "Count-in, N beats left"; it is also the hover tooltip. Every per-frame read happens inside `drawBehind`.
 */
@Composable
internal fun RecordButton(
    state: RecordButtonState,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
    countInBeatsLeft: Int = -1,
    enabled: Boolean = true,
    size: Dp = TembraTheme.spacing.touchTargetMin,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = PerfAnchors.TRANSPORT_RECORD,
    /** The transport's telemetry: the count-in's beats left per frame, so the pulse lands on the beat (spec FR-015). */
    telemetry: TransportTelemetry? = null,
) {
    if (state == RecordButtonState.Hidden) return
    val colors = TembraTheme.colors
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val reduceMotion = TembraTheme.motion.reduceMotion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val description = when (state) {
        RecordButtonState.Hidden, RecordButtonState.Idle -> TransportCopy.record
        RecordButtonState.CountIn -> TransportCopy.countInBeatsLeft(countInBeatsLeft)
        RecordButtonState.Recording -> TransportCopy.recording
    }
    val pulsing = state == RecordButtonState.CountIn && !reduceMotion
    val tick = rememberFrameTick(active = pulsing)
    val snapshot = remember { TransportSnapshot() }
    // The ring restarts on every beat: the frame the count changed on is the pulse's origin (kept in the draw phase).
    val pulse = remember { LongArray(2) { Long.MIN_VALUE } }
    val fill = when {
        enabled && (hovered || pressed) -> colors.surfaceContainerHigh
        else -> null
    }
    val record = if (enabled) daw.record else daw.record.copy(alpha = DisabledAlpha)
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    TembraTooltip(text = description, interactionSource = source, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .drawBehind {
                    val radius = this.size.minDimension / 2
                    if (fill != null) drawCircle(color = fill, radius = radius)
                    if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(radius))
                    val disc = DiscRadius.toPx()
                    when (state) {
                        RecordButtonState.Hidden -> Unit
                        RecordButtonState.Idle -> drawCircle(record, radius = disc, style = Stroke(StrokeWidth.toPx()))
                        RecordButtonState.CountIn -> {
                            drawCircle(record, radius = disc)
                            if (pulsing) {
                                val frameNanos = tick.longValue
                                var beats = countInBeatsLeft
                                if (telemetry != null) {
                                    telemetry.read(frameNanos, snapshot)
                                    if (snapshot.countInBeatsLeft >= 0) beats = snapshot.countInBeatsLeft
                                }
                                if (beats.toLong() != pulse[PULSE_BEATS]) {
                                    pulse[PULSE_BEATS] = beats.toLong()
                                    pulse[PULSE_START] = frameNanos
                                }
                                val start = pulse[PULSE_START]
                                val elapsed = if (start == Long.MIN_VALUE) PULSE_NANOS else (frameNanos - start).coerceIn(0L, PULSE_NANOS)
                                val progress = elapsed.toFloat() / PULSE_NANOS
                                if (progress < 1f) {
                                    drawCircle(
                                        record.copy(alpha = (1f - progress) * PULSE_ALPHA),
                                        radius = disc + PulseReach.toPx() * progress,
                                        style = Stroke(StrokeWidth.toPx()),
                                    )
                                }
                            } else {
                                drawCircle(record.copy(alpha = PULSE_ALPHA), radius = disc + PulseReach.toPx() / 2, style = Stroke(StrokeWidth.toPx()))
                            }
                        }
                        RecordButtonState.Recording -> {
                            if (!reduceMotion) drawCircle(record.copy(alpha = GLOW_ALPHA), radius = disc + GlowReach.toPx())
                            drawCircle(record, radius = disc)
                        }
                    }
                }
                .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onRecord)
                .semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                }
                .testTag(testTag),
        )
    }
}

/** The record circle: a 20 dp disc inside the 44 dp target, like the 24 dp glyphs of the other transport buttons. */
private val DiscRadius = 10.dp
private val StrokeWidth = 2.dp
private val PulseReach = 8.dp
private val GlowReach = 6.dp
private const val PULSE_NANOS = 400_000_000L
private const val PULSE_START = 0
private const val PULSE_BEATS = 1
private const val PULSE_ALPHA = 0.7f
private const val GLOW_ALPHA = 0.3f
private const val DisabledAlpha = 0.38f
