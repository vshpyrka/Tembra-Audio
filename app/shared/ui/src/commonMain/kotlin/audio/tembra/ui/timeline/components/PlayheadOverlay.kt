package audio.tembra.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.timeline.TimeFormat
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.engine.TransportSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The playhead line over the ruler and the lanes (spec FR-011; research R7; Constitution II): one `Canvas` whose
 * draw lambda reads [telemetry] once per frame while [playing] (inside `PerfTrace.section(TIMELINE_PLAYHEAD_FRAME)`)
 * and draws the 1.5 dp `daw.playhead` line at the audible position; stopped, it draws at [positionBeats]. The line
 * is information and keeps moving under reduce motion. Its `stateDescription` is the 10 Hz "bar.beat".
 */
@Composable
internal fun PlayheadOverlay(
    viewport: TimelineViewport,
    signature: TimeSignature,
    playing: Boolean,
    positionBeats: Beats,
    telemetry: TransportTelemetry,
    modifier: Modifier = Modifier,
) {
    val color = TembraTheme.daw.playhead
    val tick = rememberFrameTick(active = playing)
    val snapshot = remember { TransportSnapshot() }
    val described = TimeFormat.barBeat(positionBeats, signature)
    Canvas(
        modifier = modifier
            .testTag(TimelineTestTags.PLAYHEAD)
            .semantics {
                contentDescription = PLAYHEAD_DESCRIPTION
                stateDescription = described
            },
    ) {
        val beats = if (playing) {
            PerfTrace.section(PerfSection.TIMELINE_PLAYHEAD_FRAME) {
                telemetry.read(tick.longValue, snapshot)
                Beats(snapshot.positionBeats)
            }
        } else {
            positionBeats
        }
        val x = viewport.xOf(beats).toFloat()
        if (x >= 0f && x <= size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = PLAYHEAD_WIDTH_DP.dp.toPx())
        }
    }
}

internal const val PLAYHEAD_DESCRIPTION = "Playhead"
private const val PLAYHEAD_WIDTH_DP = 1.5f
