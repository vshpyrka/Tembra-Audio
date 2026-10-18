package audio.tembra.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme

/**
 * The transport bar (contracts/timeline-screen.md; the arranger tablet bar): on `surface_level1` with the
 * `surface_level2_border` seam above, [height] tall, the master volume at the start, skip-to-start, one bar back,
 * the metronome's play/pause button (tagged `transport.play`), one bar forward, the time readout and the metronome
 * toggle. Stateless: [state] and [callbacks] come from the owning screen, [telemetry] feeds the readout per frame.
 */
@Composable
internal fun TransportBar(
    state: TransportUiState,
    callbacks: TransportCallbacks,
    telemetry: TransportTelemetry,
    modifier: Modifier = Modifier,
    height: Dp = TembraTheme.spacing.transportHeightCompact,
) {
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val enabled = state.transportEnabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(daw.surfaceLevel1)
            .padding(horizontal = spacing.sm)
            .testTag(ROOT_TAG),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MasterVolumeSlider(
            percent = state.masterVolume,
            onVolume = callbacks.onMasterVolume,
            modifier = Modifier.widthIn(min = MasterSliderMinWidth, max = MasterSliderMaxWidth).weight(1f, fill = false),
        )
        TransportButton(
            icon = TembraIcon.SkipPrevious,
            description = TransportCopy.skipToStart,
            onClick = callbacks.onSkipToStart,
            enabled = enabled,
            testTag = PerfAnchors.TRANSPORT_SKIP_START,
        )
        TransportButton(
            icon = TembraIcon.FastRewind,
            description = TransportCopy.stepBack,
            onClick = { callbacks.onStepBar(-1) },
            enabled = enabled,
            testTag = STEP_BACK_TAG,
        )
        PlayPauseButton(
            running = state.isPlaying,
            onToggle = callbacks.onPlayPause,
            diameter = PlayDiameter,
            testTag = PerfAnchors.TRANSPORT_PLAY,
            startDescription = TransportCopy.play,
            stopDescription = TransportCopy.pause,
        )
        TransportButton(
            icon = TembraIcon.FastForward,
            description = TransportCopy.stepForward,
            onClick = { callbacks.onStepBar(1) },
            enabled = enabled,
            testTag = STEP_FORWARD_TAG,
        )
        TimeReadout(
            positionSeconds = state.positionSeconds,
            lengthSeconds = state.lengthSeconds,
            playing = state.isPlaying,
            telemetry = telemetry,
            modifier = Modifier.weight(1f, fill = false),
        )
        MetronomeToggle(
            on = state.metronomeOn,
            onToggle = callbacks.onMetronomeToggle,
            onSettings = callbacks.onMetronomeSettings,
            enabled = enabled,
        )
    }
}

internal const val ROOT_TAG = "transport.bar"
internal const val STEP_BACK_TAG = "transport.stepBack"
internal const val STEP_FORWARD_TAG = "transport.stepForward"

/** The play button inside a 64 dp bar: 48 dp, the landscape metronome block's smallest size. */
private val PlayDiameter = 48.dp
private val MasterSliderMinWidth = 72.dp
private val MasterSliderMaxWidth = 160.dp
