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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
 * Tagged `transport.readout` for the journeys.
 */
@Composable
internal fun TimeReadout(
    positionSeconds: Double,
    lengthSeconds: Double,
    playing: Boolean,
    telemetry: TransportTelemetry,
    modifier: Modifier = Modifier,
) {
    val style: TextStyle = TembraTheme.typographyExtras.tabularTimecode.copy(color = TembraTheme.colors.onSurface)
    val measurer = rememberTextMeasurer()
    val tick = rememberFrameTick(active = playing)
    val snapshot = remember { TransportSnapshot() }
    val length = TimeFormat.clock(lengthSeconds)
    val announced = "${TimeFormat.clock(floor(positionSeconds))} of ${TimeFormat.clock(floor(lengthSeconds))}"
    // Width of the widest possible text, so the readout never jitters as digits change.
    val template = measurer.measure("88:88.88 / 88:88.88", style)
    val density = LocalDensity.current
    val widthDp = with(density) { template.size.width.toDp() }
    val heightDp = with(density) { template.size.height.toDp() }
    Canvas(
        modifier = modifier
            .width(widthDp + 2.dp)
            .height(heightDp)
            .testTag(READOUT_TAG)
            .semantics { contentDescription = announced },
    ) {
        val frame = tick.longValue
        val seconds = if (playing) {
            telemetry.read(frame, snapshot)
            snapshot.audiblePositionSeconds.coerceAtLeast(0.0)
        } else {
            positionSeconds
        }
        val text = "${TimeFormat.clock(seconds)} / $length"
        val layout = measurer.measure(text, style)
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2))
    }
}

internal const val READOUT_TAG = "transport.readout"
