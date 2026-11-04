package audio.tembra.ui.transport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.engine.TransportSnapshot
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.floor

/**
 * "mm:ss.hh / mm:ss.hh" in `tabularTimecode` (spec FR-004; research R7): a `Canvas` that reads [telemetry] in its draw
 * lambda every frame while [playing] (a `Text` recomposed per frame is forbidden by Constitution II) and paints the
 * audible position with `drawText`; stopped, it paints the 10 Hz [positionSeconds]. The accessible name comes from the
 * whole seconds of [positionSeconds] and [lengthSeconds], so screen readers hear a value that changes once a second.
 * [stacked] paints the position above the length in a smaller size: the shape a phone-width bar needs so that the
 * seven controls of FR-004 keep their 48 dp targets (the one-line readout alone is wider than what 360–411 dp leave).
 * [maxWidth] is the room the bar can give: a system font scale that would push the digits wider shrinks them to fit
 * instead of running under the neighbouring buttons (spec FR-059, "readout intact").
 * Tagged `transport.readout` for the journeys.
 */
@Composable
internal fun TimeReadout(
    positionSeconds: Double,
    lengthSeconds: Double,
    playing: Boolean,
    telemetry: TransportTelemetry,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    maxWidth: Dp = Dp.Unspecified,
    countInBeatsLeft: Int = -1,
) {
    val base = TembraTheme.typographyExtras.tabularTimecode.copy(color = TembraTheme.colors.onSurface)
    val wanted: TextStyle = if (stacked) base.copy(fontSize = STACKED_SIZE, lineHeight = STACKED_LINE_HEIGHT) else base
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val countingIn = countInBeatsLeft >= 0
    val countInText = if (countingIn) TransportCopy.countIn(countInBeatsLeft) else ""
    // While the count-in runs the position half is "Count-in N", wider than the clock: measure with it.
    val templateText = when {
        stacked -> if (countingIn) countInText else "88:88.88"
        else -> if (countingIn) "$countInText / 88:88.88" else "88:88.88 / 88:88.88"
    }
    val style: TextStyle = fitted(wanted, templateText, maxWidth, measurer, density)
    // The count-in runs with the session stopped: the tick stays live so the beats left come from the engine per frame.
    val tick = rememberFrameTick(active = playing || countingIn)
    val snapshot = remember { TransportSnapshot() }
    val length = TimeFormat.clock(lengthSeconds)
    val announcedPosition = if (countingIn) countInText else TimeFormat.clock(floor(positionSeconds))
    val announced = "$announcedPosition of ${TimeFormat.clock(floor(lengthSeconds))}"
    // Width of the widest possible text, so the readout never jitters as digits change.
    val template = measurer.measure(templateText, style)
    val widthDp = with(density) { template.size.width.toDp() }
    val lineDp = with(density) { template.size.height.toDp() }
    val heightDp = if (stacked) lineDp * 2 else lineDp
    Canvas(
        modifier = modifier
            .width(widthDp + 2.dp)
            .height(heightDp)
            .testTag(READOUT_TAG)
            .semantics { contentDescription = announced },
    ) {
        val frame = tick.longValue
        var beatsLeft = countInBeatsLeft
        val seconds = if (countingIn) {
            // The engine's count-down lands on the beat; the state's value is the 10 Hz fallback (spec FR-015).
            telemetry.read(frame, snapshot)
            if (snapshot.countInBeatsLeft >= 0) beatsLeft = snapshot.countInBeatsLeft
            positionSeconds
        } else if (playing) {
            telemetry.read(frame, snapshot)
            snapshot.audiblePositionSeconds.coerceAtLeast(0.0)
        } else {
            positionSeconds
        }
        val positionText = if (countingIn) TransportCopy.countIn(beatsLeft) else TimeFormat.clock(seconds)
        if (stacked) {
            val position = measurer.measure(positionText, style)
            val total = measurer.measure(length, style)
            val lineHeight = size.height / 2
            drawText(position, topLeft = Offset((size.width - position.size.width) / 2, (lineHeight - position.size.height) / 2))
            drawText(total, topLeft = Offset((size.width - total.size.width) / 2, lineHeight + (lineHeight - total.size.height) / 2))
        } else {
            val layout = measurer.measure("$positionText / $length", style)
            drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2))
        }
    }
}

/**
 * [wanted] scaled down until [template] measures within [maxWidth] (two passes: glyph advances round to whole pixels,
 * so one linear estimate can land a pixel or two over), never below [MIN_FIT] of its size; unchanged when the room is
 * unspecified, the size is unspecified, or the text already fits.
 */
private fun fitted(wanted: TextStyle, template: String, maxWidth: Dp, measurer: TextMeasurer, density: Density): TextStyle {
    if (!maxWidth.isSpecified || !wanted.fontSize.isSpecified) return wanted
    var fit = 1f
    var style = wanted
    repeat(FIT_PASSES) {
        val width = with(density) { measurer.measure(template, style).size.width.toDp() } + 2.dp
        if (width <= maxWidth || fit <= MIN_FIT) return style
        fit = (fit * ((maxWidth - FIT_MARGIN) / width)).coerceAtLeast(MIN_FIT)
        style = wanted.copy(fontSize = wanted.fontSize * fit, lineHeight = if (wanted.lineHeight.isSpecified) wanted.lineHeight * fit else wanted.lineHeight)
    }
    return style
}

/** The stacked lines: the timecode scale three points smaller (about 56 dp wide), tight leading, still tabular. */
private val STACKED_SIZE = 12.sp
private val STACKED_LINE_HEIGHT = 15.sp
/** The digits never shrink below half their size: past that the bar, not the readout, is the problem. */
private const val MIN_FIT = 0.5f
/** A fitted text measures a pixel or so wider than the linear estimate; the budget is met with this much to spare. */
private val FIT_MARGIN = 1.dp
/** Two passes: the second corrects the rounding of the first. */
private const val FIT_PASSES = 2

/** The readout's tag: a journey anchor (`PerfAnchors.TRANSPORT_READOUT`) and what the bar tests read. */
internal const val READOUT_TAG = "transport.readout"
